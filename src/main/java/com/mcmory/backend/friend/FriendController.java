package com.mcmory.backend.friend;

import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;

import com.mcmory.backend.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "친구",
		description = "발송자가 선물을 보낼 친구를 등록·수정·삭제하고, 취향 요약을 저장하거나 수신자 설문 링크를 발급함. 모든 엔드포인트는 인증 필수이며 로그인한 사용자가 등록한 친구만 다룸. 실패는 `message`가 아니라 응답의 `code`로 분기할 것. 주요 코드는 `FRIEND400_1`(이름 길이), `FRIEND400_2`(전화번호 형식), `FRIEND404_1`(친구 없음 또는 다른 사용자의 친구), `FRIEND409_1`(전화번호 중복), `FRIEND409_2`(수정 시 번호 불일치)임.")
@SecurityRequirement(name = OpenApiConfig.ACCESS_COOKIE)
@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

	private final FriendService friends;

	private final CurrentMember currentMember;

	public FriendController(FriendService friends, CurrentMember currentMember) {
		this.friends = friends;
		this.currentMember = currentMember;
	}

	@Schema(name = "FriendCreateRequest", description = "친구 등록 요청임")
	public record CreateRequest(
			@Schema(description = "친구 이름. 앞뒤 공백을 제거한 뒤 1자 이상 20자 이하여야 함(컬럼이 VARCHAR(20)임). 벗어나면 FRIEND400_1임",
					example = "김민지", requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "친구 전화번호. `010`으로 시작하는 10에서 11자리이며 하이픈과 공백은 허용하되 숫자만 저장함. 형식이 어긋나면 FRIEND400_2, 같은 회원이 같은 번호를 다시 등록하면 FRIEND409_1임",
					example = "010-1234-5678", requiredMode = Schema.RequiredMode.REQUIRED) String phone) {
	}

	@Schema(name = "FriendIdRequest", description = "친구 삭제 요청임")
	public record IdRequest(@Schema(
			description = "삭제할 친구의 id. 목록 조회(`GET /api/v1/friends`)의 `result.list[].id`임. 없는 친구와 남의 친구를 같은 FRIEND404_1로 다룸 — 갈라주면 id를 순번으로 훑어 남의 친구 존재 여부를 알아낼 수 있음",
			example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long id) {
	}

	@Schema(name = "FriendTasteRequest", description = "발송자가 고른 취향 요약을 친구에게 귀속하는 요청임")
	public record TasteRequest(
			@Schema(description = "취향을 귀속할 친구의 id. 없는 친구와 남의 친구는 FRIEND404_1임", example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED) Long id,
			@Schema(description = "취향 요약 문자열임. **선택임 — 빠지면 빈 요약으로 저장함**(값 없음을 실패로 보지 않고 재호출이 안전해야 하기 때문임). 화면이 고른 값을 사람이 읽는 한 줄로 이어 붙인 것이며 서버가 값 집합을 검증하지 않음. 취향 색상 6종(코냑·블랙·베이지·핑크·골드·그레이)과 옷 스타일 6종(캐주얼·미니멀·스트릿·클래식·러블리·포멀)과 가방 4종(숄더백·토트백·크로스바디·백팩)이 재료임. **이 색상 축은 편지지 색 4종(GOLD·BLACK·BEIGE·PINK)과도 상품 색상과도 다른 축이라 섞지 말 것.** 함정 — 수신자 본인이 답한 취향(`source`가 `INVITE_ANSWER`)이 이미 있으면 이 값을 저장하지 않고 200에 `updated`를 false로 돌려줌",
					example = "블랙, 미니멀", requiredMode = Schema.RequiredMode.NOT_REQUIRED) String tasteSummary) {
	}

	@Schema(name = "FriendUpdateRequest", description = "친구 수정 요청임. **이름만 바뀜**")
	public record UpdateRequest(
			@Schema(description = "바꿀 이름. 앞뒤 공백 제거 후 1자 이상 20자 이하여야 하며 벗어나면 FRIEND400_1임", example = "김민지",
					requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "현재 전화번호임. **바꿀 값이 아니라 대조용이라 숫자가 같아야 함** — 하이픈 표기 차이는 허용하고 숫자가 다르면 FRIEND409_2로 거절하고 새 등록을 안내함(번호가 바뀌면 다른 사람으로 봄). 빼면 FRIEND400_2임",
					example = "010-1234-5678", requiredMode = Schema.RequiredMode.REQUIRED) String phone) {
	}

	@Operation(summary = "친구 목록 조회",
			description = "로그인한 회원의 친구를 취향 요약(`tasteSummary`)과 함께 반환함. 인증 필수이며 세션이 없으면 AUTH401_1임. 삭제된 친구는 개인정보 필드가 파기돼 목록에 나오지 않음")
	@ApiResponse(responseCode = "200", description = "조회 성공. `result.list`의 각 원소는 id·name·phone·tasteSummary임",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "성공", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": {
					    "list": [
					      { "id": 1, "name": "김민지", "phone": "01012345678", "tasteSummary": "블랙, 미니멀" }
					    ]
					  }
					}""")))
	@ApiResponse(responseCode = "401", description = "로그인 필요함",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AUTH401_1", value = """
					{
					  "isSuccess": false,
					  "code": "AUTH401_1",
					  "message": "로그인이 필요합니다",
					  "result": null
					}""")))
	@GetMapping
	public CustomResponse<Map<String, Object>> list() {
		List<FriendService.FriendView> list = this.friends.list(this.currentMember.requireId());
		return CustomResponse.ok(Map.of("list", list));
	}

	/** FR-004 친구 등록임. */
	@Operation(summary = "친구 등록",
			description = "이름과 전화번호로 친구를 등록함. 인증 필수임. `name`은 1자에서 20자, `phone`은 `010` 시작 10에서 11자리이며 하이픈과 공백을 허용함. 같은 번호를 다시 등록하면 FRIEND409_1임")
	@ApiResponse(responseCode = "200", description = "등록 성공",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "성공", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": {
					    "ok": true,
					    "friend": { "id": 1, "name": "김민지", "phone": "01012345678" }
					  }
					}""")))
	@ApiResponse(responseCode = "400", description = "이름 길이 또는 전화번호 형식 오류임",
			content = @Content(mediaType = "application/json",
					examples = { @ExampleObject(name = "FRIEND400_1", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND400_1",
							  "message": "이름은 1자에서 20자까지 입력해주세요",
							  "result": null
							}"""), @ExampleObject(name = "FRIEND400_2", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND400_2",
							  "message": "전화번호 형식을 확인해주세요",
							  "result": null
							}""") }))
	@ApiResponse(responseCode = "409", description = "이미 등록한 친구의 전화번호임",
			content = @Content(mediaType = "application/json",
					examples = @ExampleObject(name = "FRIEND409_1", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND409_1",
							  "message": "이미 등록한 친구의 전화번호입니다",
							  "result": null
							}""")))
	@ApiResponse(responseCode = "401", description = "로그인 필요함",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AUTH401_1", value = """
					{
					  "isSuccess": false,
					  "code": "AUTH401_1",
					  "message": "로그인이 필요합니다",
					  "result": null
					}""")))
	@PostMapping
	public CustomResponse<Map<String, Object>> create(@RequestBody CreateRequest request) {
		Friend created = this.friends.create(this.currentMember.requireId(), request.name(), request.phone());
		return CustomResponse.ok(Map.of("ok", true, "friend",
				Map.of("id", created.getId(), "name", created.getName(), "phone", created.getPhone())));
	}

	/** FR-006 삭제임. ADR-003에 따라 개인정보는 즉시 파기함. */
	@Operation(summary = "친구 삭제",
			description = "친구를 soft delete하되 `name`과 `phone`은 DB에서 즉시 NULL로 파기함. 편지함 표시에서만 `삭제된 친구`로 남음. 인증 필수이고 남의 친구 id는 FRIEND404_1임.")
	@ApiResponse(responseCode = "200", description = "삭제 성공",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "성공", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": { "ok": true }
					}""")))
	@ApiResponse(responseCode = "404", description = "친구 정보를 찾을 수 없음(없는 친구 또는 남의 친구)",
			content = @Content(mediaType = "application/json",
					examples = @ExampleObject(name = "FRIEND404_1", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND404_1",
							  "message": "친구 정보를 찾을 수 없습니다",
							  "result": null
							}""")))
	@ApiResponse(responseCode = "401", description = "로그인 필요함",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AUTH401_1", value = """
					{
					  "isSuccess": false,
					  "code": "AUTH401_1",
					  "message": "로그인이 필요합니다",
					  "result": null
					}""")))
	@DeleteMapping
	public CustomResponse<Map<String, Object>> delete(@RequestBody IdRequest request) {
		this.friends.erase(this.currentMember.requireId(), request.id());
		return CustomResponse.ok(Map.of("ok", true));
	}

	/**
	 * FR-005 친구 수정임. 경로 변수가 있어 아래 취향 저장(`PATCH /api/v1/friends`)과 패턴이 갈리므로 매핑이 모호해지지 않음 —
	 * **취향 저장 경로를 건드리지 말 것.** 프론트가 그 계약에 이미 묶여 있음.
	 */
	@Operation(summary = "친구 수정(이름만)",
			description = "친구의 이름만 바꿈. 인증 필수임. `phone`을 함께 보내되 **숫자가 같아야 함** — 하이픈 표기 차이는 허용하고 숫자가 다르면 FRIEND409_2로 거절하고 새 등록을 안내함(번호가 바뀌면 다른 사람). `phone`을 빼면 FRIEND400_2임.")
	@ApiResponse(responseCode = "200", description = "수정 성공",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "성공", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": {
					    "ok": true,
					    "friend": { "id": 1, "name": "김민지", "phone": "01012345678" }
					  }
					}""")))
	@ApiResponse(responseCode = "400", description = "이름 길이 또는 전화번호 형식 오류임(`phone` 누락 포함)",
			content = @Content(mediaType = "application/json",
					examples = { @ExampleObject(name = "FRIEND400_1", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND400_1",
							  "message": "이름은 1자에서 20자까지 입력해주세요",
							  "result": null
							}"""), @ExampleObject(name = "FRIEND400_2", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND400_2",
							  "message": "전화번호 형식을 확인해주세요",
							  "result": null
							}""") }))
	@ApiResponse(responseCode = "404", description = "친구 정보를 찾을 수 없음",
			content = @Content(mediaType = "application/json",
					examples = @ExampleObject(name = "FRIEND404_1", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND404_1",
							  "message": "친구 정보를 찾을 수 없습니다",
							  "result": null
							}""")))
	@ApiResponse(responseCode = "409", description = "전화번호 숫자가 달라 수정 불가함 — 새 등록을 안내할 것",
			content = @Content(mediaType = "application/json",
					examples = @ExampleObject(name = "FRIEND409_2", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND409_2",
							  "message": "전화번호가 다른 친구는 새로 등록해주세요",
							  "result": null
							}""")))
	@ApiResponse(responseCode = "401", description = "로그인 필요함",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AUTH401_1", value = """
					{
					  "isSuccess": false,
					  "code": "AUTH401_1",
					  "message": "로그인이 필요합니다",
					  "result": null
					}""")))
	@PatchMapping("/{id}")
	public CustomResponse<Map<String, Object>> rename(@PathVariable Long id, @RequestBody UpdateRequest request) {
		Friend updated = this.friends.rename(this.currentMember.requireId(), id, request.name(), request.phone());
		return CustomResponse.ok(Map.of("ok", true, "friend",
				Map.of("id", updated.getId(), "name", updated.getName(), "phone", updated.getPhone())));
	}

	@Schema(name = "FriendSurveyRequest",
			description = "`HOME-02` 질문 선별 요청임. **본문 전체가 선택임** — 안 보내면 저장된 선택을 그대로 두고 링크만 발급함")
	public record SurveyRequest(@Schema(
			description = "수신자에게 물어볼 축임. 허용값 셋은 `colors`·`styles`·`bags`이고 순서는 무시함. **빈 배열은 생략과 다르게 `FRIEND400_4`임** — 생략은 저장값 유지이고 빈 배열은 축을 하나도 안 켠 것임. `colors`와 `styles`를 둘 다 끄는 것과 허용값 밖의 축도 같은 코드임(가방만으로는 추천이 안 바뀜). 저장된 적이 없는 친구는 세 축 전부임",
			example = "[\"colors\",\"styles\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<String> axes) {
	}

	/** `Start-02` 설문 링크 발급임. 오리진은 서버가 모르므로 `path`만 주고 앞자리는 화면이 붙임. */
	@Operation(summary = "수신자 설문 링크 발급",
			description = "`Start-02` 설문 토큰을 멱등으로 발급함. 인증 필수이고 남의 친구도 FRIEND404_1임. **오리진은 서버가 모르므로 `path`만 주고 앞자리는 화면이 붙임.** 발급한 링크를 발송자가 문자로 보내면 수신자가 비회원 경로(`GET`과 `POST /api/v1/surveys/{token}`)에서 동의하고 취향에 답함.\n\n**`axes`는 `HOME-02` 질문 선별임.** 켠 축만 `GET /api/v1/surveys/{token}`이 돌려주고 그 밖의 축에 답하면 제출이 `FRIEND400_4`로 막힘. **토큰은 멱등이고 `axes`는 보낸 호출에서만 덮어씀** — 토글을 고쳐 다시 저장하는 것이 정상 경로이고 이미 보낸 링크는 살아 있어야 함. 축을 안 보낸 호출은 저장값을 유지함")
	@ApiResponse(responseCode = "200", description = "발급 성공. `token`은 24자이고 `path`는 `/s/{token}`이며 `axes`는 저장된 질문 선별임",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "성공", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": {
					    "token": "abcdefghijklmnopqrstuvwx",
					    "path": "/s/abcdefghijklmnopqrstuvwx",
					    "axes": ["colors", "styles"]
					  }
					}""")))
	@ApiResponse(responseCode = "400", description = "`FRIEND400_4` — 허용값 밖의 축이거나 빈 배열이거나 `colors`와 `styles`를 둘 다 끔",
			content = @Content(mediaType = "application/json",
					examples = @ExampleObject(name = "FRIEND400_4", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND400_4",
							  "message": "취향을 하나 이상 선택해주세요",
							  "result": null
							}""")))
	@ApiResponse(responseCode = "404", description = "친구 정보를 찾을 수 없음(없는 친구, 삭제된 친구, 남의 친구 모두 같은 코드임)",
			content = @Content(mediaType = "application/json",
					examples = @ExampleObject(name = "FRIEND404_1", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND404_1",
							  "message": "친구 정보를 찾을 수 없습니다",
							  "result": null
							}""")))
	@ApiResponse(responseCode = "401", description = "로그인 필요함",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AUTH401_1", value = """
					{
					  "isSuccess": false,
					  "code": "AUTH401_1",
					  "message": "로그인이 필요합니다",
					  "result": null
					}""")))
	@PostMapping("/{id}/survey")
	public CustomResponse<Map<String, Object>> issueSurveyLink(@PathVariable Long id,
			@RequestBody(required = false) SurveyRequest request) {
		var link = this.friends.issueSurveyToken(this.currentMember.requireId(), id,
				(request == null) ? null : request.axes());
		return CustomResponse.ok(Map.of("token", link.token(), "path", "/s/" + link.token(), "axes", link.axes()));
	}

	/**
	 * FR-011 취향 저장 귀속임(ADR-009). 수신자 본인 답변은 덮지 않고 updated를 false로 돌려주므로 화면이 그 값을 보고 안내해야
	 * 함.
	 */
	@Operation(summary = "취향 저장 귀속",
			description = "발송자가 고른 취향 요약을 친구에게 귀속해 저장함. 인증 필수임. `tasteSummary`가 없으면 빈 요약으로 저장됨. **수신자 본인이 답한 취향(`source`가 `INVITE_ANSWER`)이 이미 있으면 덮지 않고 `updated`를 false로 돌려줌** — 200만 보고 저장됐다고 오인하지 말고 화면이 `updated`를 보고 안내할 것. 409로 하지 않은 이유는 실패가 아니라 이미 더 신뢰할 값이 있는 것이고 재호출이 안전해야 하기 때문임")
	@ApiResponse(responseCode = "200", description = "저장 성공, 또는 본인 답변 보호로 저장하지 않음. 두 경우 모두 200이며 `result.updated`로 구분함",
			content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "저장됨", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": { "ok": true, "updated": true }
					}"""), @ExampleObject(name = "본인 답변 보호로 덮지 않음", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": { "ok": true, "updated": false, "reason": "INVITE_ANSWER_PROTECTED" }
					}""") }))
	@ApiResponse(responseCode = "404", description = "친구 정보를 찾을 수 없음",
			content = @Content(mediaType = "application/json",
					examples = @ExampleObject(name = "FRIEND404_1", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND404_1",
							  "message": "친구 정보를 찾을 수 없습니다",
							  "result": null
							}""")))
	@ApiResponse(responseCode = "401", description = "로그인 필요함",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AUTH401_1", value = """
					{
					  "isSuccess": false,
					  "code": "AUTH401_1",
					  "message": "로그인이 필요합니다",
					  "result": null
					}""")))
	@PatchMapping
	public CustomResponse<Map<String, Object>> saveTaste(@RequestBody TasteRequest request) {
		boolean updated = this.friends.saveTaste(this.currentMember.requireId(), request.id(), request.tasteSummary());

		return CustomResponse.ok(updated ? Map.of("ok", true, "updated", true)
				: Map.of("ok", true, "updated", false, "reason", "INVITE_ANSWER_PROTECTED"));
	}

}
