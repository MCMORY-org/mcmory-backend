package com.mcmory.backend.auth;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import com.mcmory.backend.member.Member;
import com.mcmory.backend.member.MemberRepository;

import jakarta.servlet.http.HttpServletRequest;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	private final MemberRepository members;

	private final CurrentMember currentMember;

	private final CookieUtils cookies;

	private final Tokens tokens;

	public AuthController(AuthService authService, MemberRepository members, CurrentMember currentMember,
			CookieUtils cookies, Tokens tokens) {
		this.authService = authService;
		this.members = members;
		this.currentMember = currentMember;
		this.cookies = cookies;
		this.tokens = tokens;
	}

	public record LoginRequest(String phone, String password) {
	}

	/**
	 * birthDate는 `yyyy-MM-dd`임. 동의는 필수 1개(개인정보)와 선택 1개(SMS)임(ADR-002 개정).
	 *
	 * 동의 필드가 원시형 boolean이 아니라 Boolean인 이유는 Jackson이 **빠진 원시형 필드를 파싱 오류로 다루기** 때문임 —
	 * smsOptIn을 생략한 요청이 우리 문구가 아니라 스프링 기본 400을 받았음(실측). 빠진 값은 여기서 false로 접음.
	 */
	public record SignupRequest(String name, String phone, String password, LocalDate birthDate, String gender,
			Boolean privacyAgreed, Boolean smsOptIn) {

		boolean agreed(Boolean value) {
			return Boolean.TRUE.equals(value);
		}

	}

	/** FR-001 회원가입임. 성공하면 로그인과 같은 응답과 같은 쿠키를 줌 — 가입 직후 로그인 상태로 들어감. */
	@PostMapping("/signup")
	public ResponseEntity<CustomResponse<Map<String, Object>>> signup(@RequestBody SignupRequest request) {
		AuthService.LoggedIn result = this.authService
			.signup(new SignupCommand(request.name(), request.phone(), request.password(), request.birthDate(),
					request.gender(), request.agreed(request.privacyAgreed()), request.agreed(request.smsOptIn())));

		return ResponseEntity.ok()
			.headers(this.cookies.headersWith(this.cookies.accessCookie(result.accessToken(), Duration.ofDays(1)),
					this.cookies.refreshCookie(result.refreshToken(), this.tokens.getRefreshTtl())))
			.body(CustomResponse.ok(Map.of("id", result.member().getId(), "name", result.member().getName())));
	}

	/** FR-002 로그인. 전화번호가 로그인 ID임(ADR-013 결정 1). */
	@PostMapping("/login")
	public ResponseEntity<CustomResponse<Map<String, Object>>> login(@RequestBody LoginRequest request) {
		AuthService.LoggedIn result = this.authService.login(request.phone(), request.password());

		return ResponseEntity.ok()
			.headers(this.cookies.headersWith(this.cookies.accessCookie(result.accessToken(), Duration.ofDays(1)),
					this.cookies.refreshCookie(result.refreshToken(), this.tokens.getRefreshTtl())))
			.body(CustomResponse.ok(Map.of("id", result.member().getId(), "name", result.member().getName())));
	}

	/** 웹뷰에서 쿠키가 유지되는지 확인하는 지점임. 미인증도 200에 member null — 프론트가 초기화에서 401을 받지 않게 함. */
	@GetMapping("/me")
	public CustomResponse<Map<String, Object>> me() {
		Long memberId = this.currentMember.findId();
		Member member = (memberId == null) ? null : this.members.findByIdAndDeletedAtIsNull(memberId).orElse(null);

		if (member == null) {
			// Map.of는 null 값을 허용하지 않음. 미인증 응답이 `{"member":null}`이어야 하므로 singletonMap을 씀
			return CustomResponse.ok(Collections.singletonMap("member", null));
		}
		return CustomResponse.ok(Map.of("member", Map.of("id", member.getId(), "name", member.getName())));
	}

	/**
	 * ADR-013 결정 9의 6항: **200 응답에 Set-Cookie가 항상 있지는 않음.** 유예 경로는 이긴 요청이 심은 쿠키를 덮지 않기 위해
	 * 액세스 토큰만 재발급하고 Set-Cookie를 보내지 않음. 프론트가 Set-Cookie 부재를 실패로 오해하면 campus 장애가 반복됨.
	 */
	@PostMapping("/reissue")
	public ResponseEntity<CustomResponse<Map<String, Object>>> reissue(HttpServletRequest request) {
		AuthService.Reissued result = this.authService.reissue(this.cookies.read(request, CookieUtils.REFRESH_COOKIE));

		if (!result.rotated()) {
			return ResponseEntity.ok()
				.headers(this.cookies.headersWith(this.cookies.accessCookie(result.accessToken(), Duration.ofDays(1))))
				.body(CustomResponse.ok(Map.of("ok", true, "rotated", false)));
		}

		return ResponseEntity.ok()
			.headers(this.cookies.headersWith(this.cookies.accessCookie(result.accessToken(), Duration.ofDays(1)),
					this.cookies.refreshCookie(result.refreshToken(), this.tokens.getRefreshTtl())))
			.body(CustomResponse.ok(Map.of("ok", true, "rotated", true)));
	}

	/** FR-029 로그아웃: 쿠키 만료와 refresh_token 행 삭제임. */
	@PostMapping("/logout")
	public ResponseEntity<CustomResponse<Map<String, Object>>> logout(HttpServletRequest request) {
		this.authService.logout(this.cookies.read(request, CookieUtils.REFRESH_COOKIE));

		return ResponseEntity.ok()
			.headers(this.cookies.headersWith(this.cookies.expiredAccessCookie(), this.cookies.expiredRefreshCookie()))
			.body(CustomResponse.ok(Map.of("ok", true)));
	}

}
