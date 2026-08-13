package com.mcmory.backend.consent;

import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import com.mcmory.backend.global.apiPayload.code.ValidationErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동의 현황과 재동의임. 이 두 경로가 버전 관리의 쓸모 그 자체다 — 버전을 올려두기만 하고 다시 묻는 경로가 없으면 버전 컬럼은 장식이 된다.
 */
@Tag(name = "동의",
		description = "항목별 동의 현황 조회와 재동의임(명세서 5.2, #6·#7). 절 전체가 `화면 미구현`이고 계약만 유지함 — 가입 1회 동의로 시연 경로가 완결되며 재동의 트리거인 약관 버전 변경이 시연에 없음. 가입의 `privacyAgreed`(#1)는 별개이며 필수임. 두 경로 모두 인증 필수이고, 미인증이면 `AUTH401_1`을 반환함. 프론트는 문구가 아니라 `code`로 분기할 것.")
@SecurityRequirement(name = OpenApiConfig.ACCESS_COOKIE)
@RestController
@RequestMapping("/api/v1/consents")
public class ConsentController {

	private final ConsentService consents;

	private final CurrentMember currentMember;

	public ConsentController(ConsentService consents, CurrentMember currentMember) {
		this.consents = consents;
		this.currentMember = currentMember;
	}

	public record ConsentRequest(@Schema(
			description = "동의 항목 코드임. 회원이 관리하는 항목은 `PRIVACY`(개인정보 수집과 이용, 가입 필수)와 `SMS`(SMS 수신, 선택) 둘뿐임. 수신자 동의(`RECIPIENT_PRIVACY`)는 회원의 것이 아니라 선물 건에 붙는 동의라 여기서 보내면 `CONSENT400_2`임",
			example = "SMS", requiredMode = Schema.RequiredMode.REQUIRED) String type,
			@Schema(description = "동의 여부임. true가 동의, false가 철회임. 함정 둘 — 생략하면 `VALID400_2`로 거부함(누락을 false로 접으면 필드를 빠뜨린 요청이 조용히 철회가 됨), 그리고 필수 항목인 `PRIVACY`에 false를 보내면 `CONSENT400_3`임",
					example = "true", requiredMode = Schema.RequiredMode.REQUIRED) Boolean agreed) {
	}

	/** needsAction이 하나라도 참이면 화면이 동의 시트를 다시 띄운다. */
	@Operation(summary = "동의 현황 조회",
			description = "항목별 동의 현황과 버전을 반환함(명세서 #6). **인증 필수** — 세션이 없으면 `AUTH401_1`임. 최상위 `needsAction`은 항목별 `needsAction`의 OR이고, 참이면 화면이 동의 시트를 다시 띄움. 항목이 참이 되는 경우는 둘 — 필수인데 동의가 없거나, 동의는 했지만 그때의 `agreedVersion`이 지금 `currentVersion`과 다름(약관 문안이 바뀌었다는 뜻)임. 함정: 버전을 올려두기만 하고 이 경로로 다시 묻지 않으면 버전 컬럼이 장식이 됨.")
	@ApiResponses({
			@ApiResponse(responseCode = "200",
					description = "동의 현황임. `result.items`가 항목 배열이고 `result.needsAction`이 재동의 필요 여부임",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "성공", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": {
									    "items": [
									      { "type": "PRIVACY", "label": "개인정보 수집과 이용", "required": true,
									        "agreed": true, "agreedVersion": "v1", "currentVersion": "v1",
									        "decidedAt": "2026-08-09T01:23:45", "needsAction": false }
									    ],
									    "needsAction": false
									  }
									}"""))),
			@ApiResponse(responseCode = "401", description = "미인증임",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1", value = """
									{
									  "isSuccess": false,
									  "code": "AUTH401_1",
									  "message": "로그인이 필요합니다",
									  "result": null
									}"""))) })
	@GetMapping
	public CustomResponse<Map<String, Object>> status() {
		List<ConsentService.ConsentStatus> items = this.consents.statusOf(this.currentMember.requireId());
		boolean needsAction = items.stream().anyMatch(ConsentService.ConsentStatus::needsAction);

		return CustomResponse.ok(Map.of("items", items, "needsAction", needsAction));
	}

	/** 재동의와 선택 항목 변경임. 기존 행을 고치지 않고 새 행을 남긴다(append-only). */
	@Operation(summary = "재동의와 선택 항목 변경",
			description = "동의 항목 하나의 동의 또는 철회를 기록함(명세서 #7). 요청은 `{ \"type\": \"SMS\", \"agreed\": true }`임. **인증 필수** — 세션이 없으면 `AUTH401_1`임. 기존 행을 고치지 않고 새 행을 남기는 append-only라 이력이 그대로 남음. 함정: `agreed`를 생략하면 `VALID400_2`로 거부함 — 누락을 false로 접으면 필드를 빠뜨린 요청이 조용히 철회로 처리되는데 동의와 철회는 감사 대상이라 명시를 요구함. 필수 항목은 철회할 수 없어 `CONSENT400_3`임.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "기록 성공임",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "성공", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": { "ok": true }
									}"""))),
			@ApiResponse(responseCode = "400", description = "본문 필드 누락 또는 동의 항목 오류임",
					content = { @Content(mediaType = "application/json",
							examples = { @ExampleObject(name = "VALID400_2 — agreed 누락", value = """
									{
									  "isSuccess": false,
									  "code": "VALID400_2",
									  "message": "필수 입력 항목을 모두 작성해주세요",
									  "result": null
									}"""), @ExampleObject(name = "CONSENT400_2 — 알 수 없는 항목", value = """
									{
									  "isSuccess": false,
									  "code": "CONSENT400_2",
									  "message": "동의 항목을 확인한 뒤 다시 시도해주세요",
									  "result": null
									}"""), @ExampleObject(name = "CONSENT400_3 — 필수 철회", value = """
									{
									  "isSuccess": false,
									  "code": "CONSENT400_3",
									  "message": "필수 동의는 철회할 수 없습니다",
									  "result": null
									}""") }) }),
			@ApiResponse(responseCode = "401", description = "미인증임",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1", value = """
									{
									  "isSuccess": false,
									  "code": "AUTH401_1",
									  "message": "로그인이 필요합니다",
									  "result": null
									}"""))) })
	@PostMapping
	public CustomResponse<Map<String, Object>> record(@RequestBody ConsentRequest request) {
		// agreed 누락을 false로 접으면 필드를 빠뜨린 요청이 조용히 "철회"로 처리됨 — 동의와 철회는 감사 대상이라 명시를 요구함
		if (request.agreed() == null) {
			throw new CustomException(ValidationErrorCode.MISSING_PARAMETER);
		}
		this.consents.record(this.currentMember.requireId(), request.type(), request.agreed());
		return CustomResponse.ok(Map.of("ok", true));
	}

}
