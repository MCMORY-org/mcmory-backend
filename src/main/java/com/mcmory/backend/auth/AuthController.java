package com.mcmory.backend.auth;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import com.mcmory.backend.member.Member;
import com.mcmory.backend.member.MemberRepository;

import jakarta.servlet.http.HttpServletRequest;

import com.mcmory.backend.global.apiPayload.CustomResponse;

import com.mcmory.backend.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증",
		description = "회원가입·로그인·세션 확인·재발급·로그아웃임. 액세스와 리프레시 토큰을 모두 HttpOnly 쿠키(`mcmory_at`, `mcmory_rt`)로 주므로 프론트는 토큰을 저장하지도 헤더에 싣지도 않고 `credentials`만 맞추면 됨(ADR-013 결정 2). 실패는 문구가 아니라 `code`로 분기할 것.")
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

	public record LoginRequest(
			@Schema(description = "전화번호임. **로그인 ID가 전화번호임**(ADR-013 결정 1) — 이메일이 아님. "
					+ "`010` 시작 10에서 11자리 숫자이며 하이픈을 넣어 보내도 서버가 숫자만 남겨 대조함", example = "01012345678",
					requiredMode = Schema.RequiredMode.REQUIRED) String phone,
			@Schema(description = "비밀번호임. 가입 때 규칙은 영문과 숫자 포함 8자 이상임. "
					+ "함정: 전화번호와 비밀번호 중 무엇이 틀렸는지 응답이 가르지 않고 `AUTH401_2` 하나로 옴 — 가입 여부를 알려주지 않기 위함임",
					example = "mcmory1234", requiredMode = Schema.RequiredMode.REQUIRED) String password) {
	}

	/**
	 * birthDate는 `yyyy-MM-dd`임. 동의는 필수 1개(개인정보)와 선택 1개(SMS)임(ADR-002 개정).
	 *
	 * 동의 필드가 원시형 boolean이 아니라 Boolean인 이유는 Jackson이 **빠진 원시형 필드를 파싱 오류로 다루기** 때문임 —
	 * smsOptIn을 생략한 요청이 우리 문구가 아니라 스프링 기본 400을 받았음(실측). 빠진 값은 여기서 false로 접음.
	 */
	public record SignupRequest(
			@Schema(description = "이름임. 앞뒤 공백을 제거해 저장하며 1자 이상 20자 이하임(컬럼이 `VARCHAR(20)`). 벗어나면 `MEMBER400_1`임",
					example = "권석", requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "전화번호임. 로그인 ID로도 쓰임. `010` 시작 10에서 11자리이며 하이픈을 넣어 보내도 되지만 "
					+ "**숫자만 저장함**(ADR-008). 형식이 어긋나면 `MEMBER400_2`, 이미 가입된 번호면 `MEMBER409_1`임",
					example = "01012345678", requiredMode = Schema.RequiredMode.REQUIRED) String phone,
			@Schema(description = "비밀번호임. **영문과 숫자를 모두 포함해 8자 이상**이어야 함. 어긋나면 `MEMBER400_3`임", example = "mcmory1234",
					requiredMode = Schema.RequiredMode.REQUIRED) String password,
			@Schema(description = "생년월일임. 형식은 `yyyy-MM-dd`임. 형식이 어긋나면 `MEMBER400_4`, "
					+ "**만 14세 미만이면 `MEMBER400_5`**로 거부함", example = "2000-03-14", format = "date",
					requiredMode = Schema.RequiredMode.REQUIRED) LocalDate birthDate,
			@Schema(description = "성별임. `MALE`·`FEMALE`·`NONE` 셋 중 하나이며 그 밖은 `MEMBER400_6`임", example = "NONE",
					allowableValues = {
							"MALE", "FEMALE", "NONE" },
					requiredMode = Schema.RequiredMode.REQUIRED) String gender,
			@Schema(description = "개인정보 수집과 이용 동의임. **필수 동의라 `true`가 아니면 `CONSENT400_1`**임(ADR-002 개정)",
					example = "true", requiredMode = Schema.RequiredMode.REQUIRED) Boolean privacyAgreed,
			@Schema(description = "SMS 수신 동의임. **선택 동의라 생략할 수 있고 생략하면 false로 접힘** — 미동의가 가입을 막지 않음. "
					+ "함정: 타입이 원시형 `boolean`이 아니라 `Boolean`인 이유는 Jackson이 빠진 원시형 필드를 파싱 오류로 다뤄, "
					+ "생략한 요청이 우리 문구가 아니라 스프링 기본 400으로 떨어졌기 때문임(실측)", example = "false",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean smsOptIn){
	}

	/**
	 * 빠진 동의 필드를 false로 접음. SignupRequest 안에 두면 record 본문이 비지 않아, 필드 설명을 붙여 헤더가 여러 줄이 된 뒤로
	 * 포매터가 내는 `){`를 checkstyle WhitespaceAround가 반려함(allowEmptyTypes가 빈 본문만 봐줌). 동작은
	 * 그대로임.
	 */
	private static boolean agreed(Boolean value) {
		return Boolean.TRUE.equals(value);
	}

	/** FR-001 회원가입임. 성공하면 로그인과 같은 응답과 같은 쿠키를 줌 — 가입 직후 로그인 상태로 들어감. */
	@Operation(summary = "회원가입",
			description = "인증 불필요임(명세서 5.1 #1). 성공 시 쿠키 2종(`mcmory_at`, `mcmory_rt`)을 발급해 가입 즉시 로그인 상태가 됨.\n\n"
					+ "필드 제약: `name` 1에서 20자, `phone`은 하이픈 허용이며 숫자만 저장(`010` 더하기 7에서 8자리), `password`는 영문과 숫자 포함 8자 이상, `birthDate`는 `yyyy-MM-dd`이며 만 14세 미만 거부, `gender`는 `MALE`·`FEMALE`·`NONE`, `privacyAgreed`는 `true`가 아니면 `CONSENT400_1`, `smsOptIn`은 선택이며 생략하면 false임.\n\n"
					+ "함정: 동의 필드가 원시형 boolean이 아니라 `Boolean`인 이유는 Jackson이 빠진 원시형 필드를 파싱 오류로 다루기 때문임 — 원시형이면 `smsOptIn` 생략이 우리 문구가 아니라 `VALID400_1`로 떨어짐.")
	@ApiResponses({
			@ApiResponse(responseCode = "200",
					description = "가입 성공임. `result`는 `{ id, name }`이고 `Set-Cookie`로 쿠키 2종이 함께 옴",
					content = @Content(examples = @ExampleObject(name = "성공", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": { "id": 1, "name": "권석" }
							}"""))),
			@ApiResponse(responseCode = "400",
					description = "`CONSENT400_1`(개인정보 동의 누락), `MEMBER400_1`부터 `MEMBER400_6`(이름·전화번호·비밀번호·생년월일·만 14세·성별)임",
					content = @Content(examples = { @ExampleObject(name = "CONSENT400_1", value = """
							{
							  "isSuccess": false,
							  "code": "CONSENT400_1",
							  "message": "개인정보 수집과 이용에 동의해주세요",
							  "result": null
							}"""), @ExampleObject(name = "MEMBER400_2", value = """
							{
							  "isSuccess": false,
							  "code": "MEMBER400_2",
							  "message": "전화번호 형식을 확인해주세요",
							  "result": null
							}"""), @ExampleObject(name = "MEMBER400_5", value = """
							{
							  "isSuccess": false,
							  "code": "MEMBER400_5",
							  "message": "만 14세 이상만 가입할 수 있습니다",
							  "result": null
							}""") })),
			@ApiResponse(responseCode = "409", description = "`MEMBER409_1` — 이미 가입된 전화번호임",
					content = @Content(examples = @ExampleObject(name = "MEMBER409_1", value = """
							{
							  "isSuccess": false,
							  "code": "MEMBER409_1",
							  "message": "이미 가입된 전화번호입니다",
							  "result": null
							}"""))) })
	@PostMapping("/signup")
	public ResponseEntity<CustomResponse<Map<String, Object>>> signup(@RequestBody SignupRequest request) {
		AuthService.LoggedIn result = this.authService
			.signup(new SignupCommand(request.name(), request.phone(), request.password(), request.birthDate(),
					request.gender(), agreed(request.privacyAgreed()), agreed(request.smsOptIn())));

		return ResponseEntity.ok()
			.headers(this.cookies.headersWith(this.cookies.accessCookie(result.accessToken(), Duration.ofDays(1)),
					this.cookies.refreshCookie(result.refreshToken(), this.tokens.getRefreshTtl())))
			.body(CustomResponse.ok(Map.of("id", result.member().getId(), "name", result.member().getName())));
	}

	/** FR-002 로그인. 전화번호가 로그인 ID임(ADR-013 결정 1). */
	@Operation(summary = "로그인",
			description = "인증 불필요임(명세서 5.1 #2). 전화번호가 로그인 ID임(ADR-013 결정 1) — 이메일이 아님.\n\n"
					+ "성공 시 액세스와 리프레시 쿠키를 함께 심음. 실패는 전화번호와 비밀번호를 가르지 않고 `AUTH401_2` 하나로 응답함 — 가입 여부를 알려주지 않기 위함임.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그인 성공임. `result`는 `{ id, name }`임",
					content = @Content(examples = @ExampleObject(name = "성공", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": { "id": 1, "name": "권석" }
							}"""))),
			@ApiResponse(responseCode = "401", description = "`AUTH401_2` — 전화번호 또는 비밀번호 불일치임",
					content = @Content(examples = @ExampleObject(name = "AUTH401_2", value = """
							{
							  "isSuccess": false,
							  "code": "AUTH401_2",
							  "message": "전화번호 또는 비밀번호가 올바르지 않습니다",
							  "result": null
							}"""))) })
	@PostMapping("/login")
	public ResponseEntity<CustomResponse<Map<String, Object>>> login(@RequestBody LoginRequest request) {
		AuthService.LoggedIn result = this.authService.login(request.phone(), request.password());

		return ResponseEntity.ok()
			.headers(this.cookies.headersWith(this.cookies.accessCookie(result.accessToken(), Duration.ofDays(1)),
					this.cookies.refreshCookie(result.refreshToken(), this.tokens.getRefreshTtl())))
			.body(CustomResponse.ok(Map.of("id", result.member().getId(), "name", result.member().getName())));
	}

	/** 웹뷰에서 쿠키가 유지되는지 확인하는 지점임. 미인증도 200에 member null — 프론트가 초기화에서 401을 받지 않게 함. */
	@Operation(summary = "세션 확인", description = "인증 선택임(명세서 5.1 #3). 웹뷰에서 쿠키가 유지되는지 확인하는 지점임.\n\n"
			+ "함정: **미인증도 401이 아니라 200**이고 `result.member`가 `null`임 — 프론트가 초기화에서 401을 받아 로그아웃 처리하지 않게 하려는 계약임. 로그인 여부는 상태 코드가 아니라 `result.member`의 null 여부로 판정할 것.")
	@ApiResponse(responseCode = "200", description = "인증이면 `member` 객체, 미인증이면 `member`가 null임",
			content = @Content(examples = { @ExampleObject(name = "인증됨", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": { "member": { "id": 1, "name": "권석" } }
					}"""), @ExampleObject(name = "미인증", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": { "member": null }
					}""") }))
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
	@Operation(summary = "토큰 재발급", description = "리프레시 쿠키(`mcmory_rt`)가 필요함(명세서 5.1 #4, 4장).\n\n"
			+ "함정: **200 응답에 `Set-Cookie`가 항상 있지는 않음**(ADR-013 결정 9의 6항). 같은 쿠키로 재발급이 동시에 두 번 들어오면 회전은 한 번만 일어나고, 진 요청은 30초 유예창 안이면 200을 받되 액세스 토큰만 재발급되고 `Set-Cookie`가 오지 않음 — 이긴 요청이 심은 쿠키를 덮으면 안 되기 때문임. 구분은 `result.rotated`임.\n\n"
			+ "**`Set-Cookie` 부재를 실패로 다루면 안 됨** — campus에서 이것을 실패로 처리해 운영 401이 211건 발생했음. 프론트는 인증 초기화를 single-flight로 하고, 로그인·콜백 화면에서 초기 재발급을 하지 않으며, **5xx를 401처럼 로그아웃 처리하지 않을 것**(ADR-013 결정 10).")
	@ApiResponses({
			@ApiResponse(responseCode = "200",
					description = "재발급 성공임. `rotated`가 false면 유예 경로이고 `Set-Cookie`에 리프레시 쿠키가 없음",
					content = @Content(examples = { @ExampleObject(name = "회전됨", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": { "ok": true, "rotated": true }
							}"""), @ExampleObject(name = "유예 경로(회전 안 됨)", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": { "ok": true, "rotated": false }
							}""") })),
			@ApiResponse(responseCode = "401",
					description = "`AUTH401_3` — 재발급 실패임. `AUTH401_1`(애초에 세션 없음)과 달라 프론트는 재발급 재시도를 멈추고 로그아웃 처리할 것",
					content = @Content(examples = @ExampleObject(name = "AUTH401_3", value = """
							{
							  "isSuccess": false,
							  "code": "AUTH401_3",
							  "message": "다시 로그인해주세요",
							  "result": null
							}"""))) })
	@SecurityRequirement(name = OpenApiConfig.REFRESH_COOKIE)
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
	@Operation(summary = "로그아웃",
			description = "인증 선택임(명세서 5.1 #5). 쿠키 2종을 만료시키고 `refresh_token` 행을 삭제함.\n\n"
					+ "함정: 쿠키가 없거나 이미 만료된 상태로 불러도 200임 — 멱등하게 설계돼 있어 프론트가 실패를 분기할 필요가 없음.")
	@ApiResponse(responseCode = "200", description = "로그아웃 성공임. 만료 쿠키 2종이 `Set-Cookie`로 옴",
			content = @Content(examples = @ExampleObject(name = "성공", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": { "ok": true }
					}""")))
	@PostMapping("/logout")
	public ResponseEntity<CustomResponse<Map<String, Object>>> logout(HttpServletRequest request) {
		this.authService.logout(this.cookies.read(request, CookieUtils.REFRESH_COOKIE));

		return ResponseEntity.ok()
			.headers(this.cookies.headersWith(this.cookies.expiredAccessCookie(), this.cookies.expiredRefreshCookie()))
			.body(CustomResponse.ok(Map.of("ok", true)));
	}

}
