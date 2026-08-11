package com.mcmory.backend.friend;

import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
		description = "발송자가 선물을 보낼 친구를 등록·수정·삭제하고, 취향 요약을 저장하거나 수신자 설문 링크를 발급하는 도메인임. 전부 인증 필수이며 자기 친구만 다룸. 실패는 문구가 아니라 봉투의 `code`로 분기할 것 — 주요 코드는 FRIEND400_1(이름 길이), FRIEND400_2(전화번호 형식), FRIEND404_1(친구 없음 또는 남의 친구), FRIEND409_1(전화번호 중복), FRIEND409_2(수정 시 번호 불일치)임. 회원당 친구 수 상한은 서버가 걸지 않으나 화면이 네 칸이라 5명째부터는 조회도 수정도 안 되는 고아 행이 됨")
@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

	private final FriendService friends;

	private final CurrentMember currentMember;

	public FriendController(FriendService friends, CurrentMember currentMember) {
		this.friends = friends;
		this.currentMember = currentMember;
	}

	public record CreateRequest(String name, String phone) {
	}

	public record IdRequest(Long id) {
	}

	public record TasteRequest(Long id, String tasteSummary) {
	}

	public record UpdateRequest(String name, String phone) {
	}

	@Operation(summary = "친구 목록 조회",
			description = "로그인한 회원의 친구를 취향 요약(`tasteSummary`)과 함께 반환함(명세서 5.5 #15). 인증 필수이며 세션이 없으면 AUTH401_1임. 삭제된 친구는 개인정보 필드가 파기돼 목록에 나오지 않음(ADR-003)")
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
			description = "이름과 전화번호로 친구를 등록함(명세서 5.5 #16, FR-004). 인증 필수임. `name`은 1자에서 20자, `phone`은 `010` 시작 10에서 11자리이며 하이픈과 공백을 허용함. 같은 번호를 다시 등록하면 FRIEND409_1임")
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
	@PostMapping
	public CustomResponse<Map<String, Object>> create(@RequestBody CreateRequest request) {
		Friend created = this.friends.create(this.currentMember.requireId(), request.name(), request.phone());
		return CustomResponse.ok(Map.of("ok", true, "friend",
				Map.of("id", created.getId(), "name", created.getName(), "phone", created.getPhone())));
	}

	/** FR-006 삭제임. ADR-003에 따라 개인정보는 즉시 파기함. */
	@Operation(summary = "친구 삭제",
			description = "친구를 soft delete하되 `name`과 `phone`은 DB에서 즉시 NULL로 파기함(명세서 5.5 #18, FR-006, ADR-003). 편지함 표시에서만 `삭제된 친구`로 남음. 인증 필수이고 남의 친구 id는 FRIEND404_1임. **화면 미구현**이라 현재 호출 지점이 없음(명세서 5.0)")
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
			description = "친구의 이름만 바꿈(명세서 5.5 #31, FR-005). 인증 필수임. `phone`을 함께 보내되 **숫자가 같아야 함** — 하이픈 표기 차이는 허용하고 숫자가 다르면 FRIEND409_2로 거절하고 새 등록을 안내함(번호가 바뀌면 다른 사람). `phone`을 빼면 FRIEND400_2임. **화면 미구현**임(명세서 5.0)")
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
	@PatchMapping("/{id}")
	public CustomResponse<Map<String, Object>> rename(@PathVariable Long id, @RequestBody UpdateRequest request) {
		Friend updated = this.friends.rename(this.currentMember.requireId(), id, request.name(), request.phone());
		return CustomResponse.ok(Map.of("ok", true, "friend",
				Map.of("id", updated.getId(), "name", updated.getName(), "phone", updated.getPhone())));
	}

	/** `Start-02` 설문 링크 발급임. 오리진은 서버가 모르므로 `path`만 주고 앞자리는 화면이 붙임. */
	@Operation(summary = "수신자 설문 링크 발급",
			description = "`Start-02` 설문 토큰을 멱등으로 발급함(명세서 5.5 #33과 5.5-1). 인증 필수이고 남의 친구도 FRIEND404_1임. **오리진은 서버가 모르므로 `path`만 주고 앞자리는 화면이 붙임.** 발급한 링크를 발송자가 문자로 보내면 수신자가 비회원 경로(`GET`과 `POST /api/v1/s/{token}`)에서 동의하고 취향에 답함")
	@ApiResponse(responseCode = "200", description = "발급 성공. `token`은 24자이고 `path`는 `/s/{token}`임",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "성공", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": {
					    "token": "abcdefghijklmnopqrstuvwx",
					    "path": "/s/abcdefghijklmnopqrstuvwx"
					  }
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
	@PostMapping("/{id}/survey")
	public CustomResponse<Map<String, Object>> issueSurveyLink(@PathVariable Long id) {
		String token = this.friends.issueSurveyToken(this.currentMember.requireId(), id);
		return CustomResponse.ok(Map.of("token", token, "path", "/s/" + token));
	}

	/**
	 * FR-011 취향 저장 귀속임(ADR-009). 수신자 본인 답변은 덮지 않고 updated를 false로 돌려주므로 화면이 그 값을 보고 안내해야
	 * 함.
	 */
	@Operation(summary = "취향 저장 귀속",
			description = "발송자가 고른 취향 요약을 친구에게 귀속해 저장함(명세서 5.5 #17, FR-011, ADR-009). 인증 필수임. `tasteSummary`가 없으면 빈 요약으로 저장됨. **수신자 본인이 답한 취향(`source`가 `INVITE_ANSWER`)이 이미 있으면 덮지 않고 `updated`를 false로 돌려줌** — 200만 보고 저장됐다고 오인하지 말고 화면이 `updated`를 보고 안내할 것. 409로 하지 않은 이유는 실패가 아니라 이미 더 신뢰할 값이 있는 것이고 재호출이 안전해야 하기 때문임")
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
	@PatchMapping
	public CustomResponse<Map<String, Object>> saveTaste(@RequestBody TasteRequest request) {
		boolean updated = this.friends.saveTaste(this.currentMember.requireId(), request.id(), request.tasteSummary());

		return CustomResponse.ok(updated ? Map.of("ok", true, "updated", true)
				: Map.of("ok", true, "updated", false, "reason", "INVITE_ANSWER_PROTECTED"));
	}

}
