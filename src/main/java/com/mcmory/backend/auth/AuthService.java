package com.mcmory.backend.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import com.mcmory.backend.global.apiPayload.code.AuthErrorCode;
import com.mcmory.backend.global.apiPayload.code.MemberErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.common.Phones;
import com.mcmory.backend.member.Member;
import com.mcmory.backend.consent.ConsentService;
import com.mcmory.backend.member.MemberRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인, 재발급(회전과 유예), 로그아웃임.
 *
 * 재발급 결과는 셋으로 갈림 — 회전 성공(새 쿠키 발급), 유예 통과(액세스만 재발급하고 Set-Cookie 없음), 실패(401).
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final MemberRepository members;

	private final RefreshTokenRepository refreshTokens;

	private final ConsentService consents;

	private final PasswordEncoder passwordEncoder;

	private final Tokens tokens;

	private final AuthProperties properties;

	public AuthService(MemberRepository members, RefreshTokenRepository refreshTokens, ConsentService consents,
			PasswordEncoder passwordEncoder, Tokens tokens, AuthProperties properties) {
		this.members = members;
		this.refreshTokens = refreshTokens;
		this.consents = consents;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.properties = properties;
	}

	/** 재발급 결과임. rotated가 false면 유예 경로이고 refreshToken은 null임(Set-Cookie를 보내지 않아야 함). */
	public record Reissued(String accessToken, String refreshToken, boolean rotated) {
	}

	public record LoggedIn(Member member, String accessToken, String refreshToken) {
	}

	@Transactional
	public LoggedIn login(String rawPhone, String rawPassword) {
		String phone = Phones.digits(rawPhone);
		Member member = this.members.findByPhoneAndDeletedAtIsNull(phone)
			.filter((found) -> this.passwordEncoder.matches(rawPassword == null ? "" : rawPassword,
					found.getPasswordHash()))
			.orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_CREDENTIALS));

		return issueSession(member);
	}

	/**
	 * FR-001 가입임. 가입 즉시 로그인시킴 — 방금 검증한 자격증명이라 별도 로그인을 강제해도 얻는 것이 없고, 프론트는 로그인 응답 처리 코드를
	 * 그대로 씀.
	 *
	 * 회원과 동의 행이 한 트랜잭션임. 동의 저장이 깨지면 회원까지 롤백해서 "회원은 있는데 동의 이력이 없는" 상태를 만들지 않음.
	 */
	@Transactional
	public LoggedIn signup(SignupCommand command) {
		command.validate();

		Member member;
		try {
			member = this.members.saveAndFlush(new Member(command.name().trim(), Phones.digits(command.phone()),
					this.passwordEncoder.encode(command.password()), command.birthDate(), command.gender(),
					command.smsOptIn()));
		}
		catch (DataIntegrityViolationException ex) {
			// phone UNIQUE에 맡김. 사전 조회는 TOCTOU 창이 있어 결국 이 catch가 또 필요해짐(FriendService와 같은
			// 판단)
			throw new CustomException(MemberErrorCode.DUPLICATE_PHONE);
		}

		this.consents.recordSignup(member.getId(), command.smsOptIn());

		return issueSession(member);
	}

	private LoggedIn issueSession(Member member) {
		// ADR-013 결정 11: 로그인마다 행을 추가함. member_id에 유니크가 없어 앞 기기가 끊기지 않음
		String refreshToken = this.tokens.newRefreshToken();
		this.refreshTokens.save(new RefreshToken(member.getId(), this.tokens.hash(refreshToken),
				LocalDateTime.now().plus(this.tokens.getRefreshTtl())));

		return new LoggedIn(member, this.tokens.issueAccessToken(member.getId()), refreshToken);
	}

	/**
	 * ADR-013 결정 9의 4항: **트랜잭션을 열지 않음**(NOT_SUPPORTED). 회전 UPDATE와 유예 조회가 한 트랜잭션에 묶이면
	 * MySQL REPEATABLE READ 스냅샷 때문에 진 요청이 이긴 요청이 방금 쓴 prev 해시를 보지 못해 유예가 무력화됨. 코드를 그대로
	 * 베껴도 이 설정 하나로 기능이 죽는 종류라 여기에 남김.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public Reissued reissue(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			throw new CustomException(AuthErrorCode.REFRESH_FAILED);
		}

		String oldHash = this.tokens.hash(rawRefreshToken);
		LocalDateTime now = LocalDateTime.now();

		// 결정 9의 3항: 회원 식별을 회전 **전에** 확보함. 회전 후 새 해시로 재조회하면 그사이 다른 기기 로그인이
		// 행을 덮어써 못 찾는 경쟁 창이 있음(campus가 실제로 밟은 함정)
		Long memberId = this.refreshTokens.findByTokenHash(oldHash).map(RefreshToken::getMemberId).orElse(null);

		String newToken = this.tokens.newRefreshToken();
		int rotated = this.refreshTokens.rotate(oldHash, this.tokens.hash(newToken), now);

		if (rotated == 1 && memberId != null) {
			return new Reissued(this.tokens.issueAccessToken(memberId), newToken, true);
		}

		// 결정 9의 1항: 회전 실패(미존재, 만료, 경합 패배)는 유예 조회로 한 번 더 판정함.
		// 유예창이 0이면 조회 자체를 하지 않음 — 길이 0인 창은 아무것도 담을 수 없다는 것이 의미인데,
		// 타임스탬프 비교(prevRotatedAt >= now)에 맡기면 회전과 재요청이 같은 밀리초에 들어올 때 유예가
		// 성립해버림(CI에서 실제로 통과했고 로컬에서는 느려서 안 났음)
		long graceSeconds = this.properties.getRefresh().getGraceSeconds();
		Optional<RefreshToken> withinGrace = (graceSeconds <= 0) ? Optional.empty()
				: this.refreshTokens.findWithinGrace(oldHash, now.minusSeconds(graceSeconds), now);

		if (withinGrace.isPresent()) {
			Long graceMemberId = withinGrace.get().getMemberId();
			// 결정 9의 7항: 유예 200과 회전 200은 응답이 같아 로그로만 구분됨. 토큰과 해시는 남기지 않음
			log.info("리프레시 유예 경로로 액세스 토큰만 재발급함 memberId={} graceSeconds={}", graceMemberId,
					this.properties.getRefresh().getGraceSeconds());
			return new Reissued(this.tokens.issueAccessToken(graceMemberId), null, false);
		}

		// 결정 9의 5항: 유예창 밖, 모르는 토큰, 로그아웃이나 탈퇴로 행이 사라진 경우는 401
		throw new CustomException(AuthErrorCode.REFRESH_FAILED);
	}

	/** ADR-013 결정 11: 요청 쿠키의 토큰 행만 지움. 전체 기기 로그아웃은 범위 밖임. */
	@Transactional
	public void logout(String rawRefreshToken) {
		if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
			this.refreshTokens.deleteByTokenHash(this.tokens.hash(rawRefreshToken));
		}
	}

}
