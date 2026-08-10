package com.mcmory.backend.consent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mcmory.backend.global.apiPayload.code.ConsentErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.member.MemberRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동의 이력과 버전 판정임.
 *
 * consent는 append-only라(ADR-002 결정 3) 항목의 현재 상태는 "그 항목의 마지막 행"이다. 행을 고치지 않으므로 철회도 새 행이고,
 * 그 덕분에 언제 무엇에 동의했는지가 그대로 남는다.
 */
@Service
public class ConsentService {

	private final ConsentRepository consents;

	private final MemberRepository members;

	public ConsentService(ConsentRepository consents, MemberRepository members) {
		this.consents = consents;
		this.members = members;
	}

	/**
	 * 항목 하나의 현재 상태임. needsAction이 참이면 화면이 다시 물어야 함 — 필수인데 동의가 없거나, 동의했지만 그때의 버전이 지금 버전과
	 * 다른 경우임.
	 */
	public record ConsentStatus(String type, String label, boolean required, boolean agreed, String agreedVersion,
			String currentVersion, LocalDateTime decidedAt, boolean needsAction) {
	}

	@Transactional(readOnly = true)
	public List<ConsentStatus> statusOf(Long memberId) {
		Map<String, Consent> latest = latestByType(memberId);

		return ConsentType.memberScoped().stream().map((type) -> {
			Consent row = latest.get(type.name());
			boolean agreed = (row != null) && row.isAgreed();
			String agreedVersion = (row == null) ? null : row.getTermsVersion();

			// 선택 항목은 미동의가 정상 상태라 재동의를 요구하지 않음. 필수만 판정 대상임.
			// 버전이 다르면 동의했더라도 다시 물어야 함 — 문안이 바뀌었다는 뜻이므로
			boolean needsAction = type.isRequiredForSignup()
					&& (!agreed || !type.currentVersion().equals(agreedVersion));

			return new ConsentStatus(type.name(), type.label(), type.isRequiredForSignup(), agreed, agreedVersion,
					type.currentVersion(), (row == null) ? null : row.getCreatedAt(), needsAction);
		}).toList();
	}

	/** 재동의와 철회임. 기존 행을 고치지 않고 새 행을 남김. */
	@Transactional
	public void record(Long memberId, String rawType, boolean agreed) {
		ConsentType type = parse(rawType);

		if (type.isRequiredForSignup() && !agreed) {
			// 필수 동의 철회는 탈퇴에 준하는 처리가 필요한데 탈퇴가 MVP 밖이라 경로를 열지 않음.
			// 여기서 조용히 받아두면 "동의 없이 쓰는 회원"이 생기고 그게 더 나쁨
			throw new CustomException(ConsentErrorCode.REQUIRED_NOT_WITHDRAWABLE);
		}

		this.consents.save(Consent.ofMember(memberId, type, agreed));

		// member.sms_opt_in은 이 이력의 현재값 캐시임(ERD). 이력만 남기고 캐시를 두면 둘이 갈라져
		// 어느 쪽이 진실인지 알 수 없게 됨 — 같은 트랜잭션에서 함께 쓴다
		if (type == ConsentType.SMS) {
			this.members.findByIdAndDeletedAtIsNull(memberId).ifPresent((member) -> member.updateSmsOptIn(agreed));
		}
	}

	/** 가입 시점의 동의 묶음임. 필수 항목과 선택 항목을 한 트랜잭션에서 함께 남김. */
	@Transactional
	public void recordSignup(Long memberId, boolean smsOptIn) {
		for (ConsentType type : ConsentType.requiredForSignup()) {
			this.consents.save(Consent.ofMember(memberId, type, true));
		}
		// 선택 항목은 false여도 "동의하지 않음" 이력으로 남김 — 물어본 적 없음과 거절을 구분해야 함
		this.consents.save(Consent.ofMember(memberId, ConsentType.SMS, smsOptIn));
	}

	private Map<String, Consent> latestByType(Long memberId) {
		Map<String, Consent> latest = new HashMap<>();
		// id 오름차순이라 뒤에 오는 것이 최신임. append-only라 id 순서가 곧 시간 순서임
		for (Consent row : this.consents.findByMemberIdOrderById(memberId)) {
			latest.put(row.getConsentType(), row);
		}
		return latest;
	}

	private ConsentType parse(String rawType) {
		// 회원 항목 목록에서 직접 찾음. valueOf로 파싱한 뒤 걸러내면 RECIPIENT_PRIVACY가 잠깐 통과하고,
		// null 처리를 예외 잡기로 대신하게 됨(SpotBugs DCN_NULLPOINTER_EXCEPTION)
		return ConsentType.memberScoped()
			.stream()
			.filter((type) -> type.name().equals(rawType))
			.findFirst()
			.orElseThrow(() -> new CustomException(ConsentErrorCode.UNKNOWN_TYPE));
	}

}
