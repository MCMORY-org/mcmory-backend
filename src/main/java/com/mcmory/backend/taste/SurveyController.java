package com.mcmory.backend.taste;

import java.util.List;
import java.util.Map;

import com.mcmory.backend.global.apiPayload.CustomResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * `Start-01`과 `Start-02`임. 초대 열람(`/api/v1/invitations/{token}`)과 같은 비회원 경로임 — 수신자는 회원이 아닐
 * 수 있음.
 */
@Tag(name = "수신자 설문",
		description = "수신자가 SMS로 받은 설문 링크에서 본인 확인과 동의(`Start-01`)를 거쳐 취향에 답하는(`Start-02`) 비회원 경로임. 토큰이 인증 주체가 아니라 리소스 식별자이자 권한 근거임(ADR-013 결정 4) — 수신자는 회원이 아닐 수 있어 로그인을 요구하지 않음. 추천은 이 답변 뒤에 나옴.")
@RestController
public class SurveyController {

	private final SurveyService survey;

	public SurveyController(SurveyService survey) {
		this.survey = survey;
	}

	/** 세 축 모두 다중 선택임(v1.1 실측). */
	public record SubmitRequest(@Schema(
			description = "개인정보 수집 동의임. `true`가 아니면 `FRIEND400_3`임. 값만 검증하고 `consent` 이력 행으로 남기지 않음 — 그 테이블의 CHECK가 `member_id`나 `gift_id`를 요구하는데 설문 시점에는 선물이 없음",
			example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean privacyAgreed,
			@Schema(description = "취향 색상 다중 선택임. 허용값 6종은 코냑·블랙·베이지·핑크·골드·그레이이고 선택지 밖 값은 조용히 버리지 않고 `FRIEND400_4`로 막음. 추천 점수에 반영됨. **편지지 색 4종(GOLD·BLACK·BEIGE·PINK), 상품 색상과는 다른 축이라 섞지 말 것**. 개별로는 선택이지만 `styles`와 둘 다 비면 `FRIEND400_4`임",
					example = "[\"코냑\",\"블랙\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<String> colors,
			@Schema(description = "옷 스타일 다중 선택임. 허용값 6종은 캐주얼·미니멀·스트릿·클래식·러블리·포멀이고 선택지 밖 값은 `FRIEND400_4`임. 추천 점수에 반영됨. 개별로는 선택이지만 `colors`와 둘 다 비면 `FRIEND400_4`임",
					example = "[\"미니멀\",\"클래식\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<String> styles,
			@Schema(description = "가방 디자인 다중 선택임. 허용값 4종은 숄더백·토트백·크로스바디·백팩이고 선택지 밖 값은 `FRIEND400_4`임. **저장만 하고 추천 점수에 쓰지 않음** — 시드 상품에 가방 디자인 축이 없어 매칭할 대상이 없음. 그래서 가방만 고른 제출은 `FRIEND400_4`로 막힘. 선택임",
					example = "[\"토트백\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<String> bags) {
	}

	@Operation(summary = "설문 표시 정보 조회 (#34, Start-01)",
			description = "인증 불필요임. `senderName`은 익명 표시명이고 지금은 더미 `멋쟁이사자` 고정임 — 발송자 실명을 토큰 소지자에게 주지 않음(ADR-001 익명 원칙). `answered`가 true면 이미 답한 설문이며, 재제출은 덮어쓰기라 차단하지 않음. **`axes`는 발송자가 `HOME-02`에서 켠 축이라 여기 없는 축은 그리지 말 것**(FEAT-W003) — 답을 담아 보내면 제출이 `FRIEND400_4`로 막힘. 없는 토큰과 삭제된 친구를 갈라주지 않고 모두 `FRIEND404_1`로 반환함.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공",
					content = @Content(examples = @ExampleObject(name = "성공", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": {
							    "senderName": "멋쟁이사자",
							    "friendName": "김민지",
							    "answered": false,
							    "axes": ["colors", "styles"]
							  }
							}"""))),
			@ApiResponse(responseCode = "404", description = "`FRIEND404_1` — 없는 토큰이거나 삭제된 친구임",
					content = @Content(examples = @ExampleObject(name = "FRIEND404_1", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND404_1",
							  "message": "친구 정보를 찾을 수 없습니다",
							  "result": null
							}"""))) })
	@GetMapping("/api/v1/surveys/{token}")
	public CustomResponse<SurveyService.SurveyView> read(@PathVariable String token) {
		return CustomResponse.ok(this.survey.read(token));
	}

	@Operation(summary = "설문 답변 제출 (#35, Start-02)",
			description = """
					인증 불필요임. 세 축 모두 다중 선택임 — `colors`(코냑, 블랙, 베이지, 핑크, 골드, 그레이), `styles`(캐주얼, 미니멀, 스트릿, 클래식, 러블리, 포멀), `bags`(숄더백, 토트백, 크로스바디, 백팩).

					함정 넷임.
					1. **발송자가 끈 축에 답을 담아 보내면 `FRIEND400_4`임**(FEAT-W003). 물어볼 축은 `GET`의 `axes`가 정본이니 그것만 그릴 것.
					2. `bags`는 저장만 하고 추천 점수에 쓰지 않음 — 시드 상품에 가방 디자인 축이 없어 매칭 대상이 없음.
					3. `colors`와 `styles`가 둘 다 비면 `FRIEND400_4`임. 선택지 밖 값도 조용히 버리지 않고 같은 코드로 막음.
					4. 브라우저에서 나가는 비인증 POST라 `OriginCheckFilter`를 지남 — 설문 페이지 오리진이 `ALLOWED_ORIGINS`에 없으면 제출이 전부 `COMMON403`임.

					저장은 `taste_profile`에 `source = INVITE_ANSWER` 1행이고 재제출은 덮어씀(ADR-009 결정 1). 이 값이 있으면 발송자의 취향 저장(#17)이 덮지 못함. 동의는 값만 검증하고 이력 행으로 남기지 않음. 취향 색상 6종은 편지지 색상 4종과 다른 축이고 상품 색상과도 값 집합이 다름 — 섞지 말 것.""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "제출 성공",
					content = @Content(examples = @ExampleObject(name = "성공", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": {
							    "ok": true
							  }
							}"""))),
			@ApiResponse(responseCode = "400", description = "`FRIEND400_3` 동의 누락, `FRIEND400_4` 취향 미선택",
					content = @Content(examples = { @ExampleObject(name = "FRIEND400_3", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND400_3",
							  "message": "개인정보 수집 동의가 필요합니다",
							  "result": null
							}"""), @ExampleObject(name = "FRIEND400_4", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND400_4",
							  "message": "취향을 하나 이상 선택해주세요",
							  "result": null
							}""") })),
			@ApiResponse(responseCode = "403", description = "`COMMON403` — Origin 검사 실패임. 필터가 봉투를 직접 씀",
					content = @Content(examples = @ExampleObject(name = "COMMON403", value = """
							{
							  "isSuccess": false,
							  "code": "COMMON403",
							  "message": "현재 접속한 환경에서는 요청을 처리할 수 없습니다. 공식 앱이나 웹에서 다시 시도해주세요",
							  "result": null
							}"""))),
			@ApiResponse(responseCode = "404", description = "`FRIEND404_1` — 없는 토큰이거나 삭제된 친구임",
					content = @Content(examples = @ExampleObject(name = "FRIEND404_1", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND404_1",
							  "message": "친구 정보를 찾을 수 없습니다",
							  "result": null
							}"""))) })
	@PostMapping("/api/v1/surveys/{token}")
	public CustomResponse<Map<String, Object>> submit(@PathVariable String token, @RequestBody SubmitRequest request) {
		this.survey.submit(token, request.privacyAgreed(), request.colors(), request.styles(), request.bags());
		return CustomResponse.ok(Map.of("ok", true));
	}

}
