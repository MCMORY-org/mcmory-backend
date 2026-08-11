package com.mcmory.backend.recommend;

import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "추천",
		description = "관계 태그와 예산으로 선물을 추천하고(#8), 그 결과를 스냅샷으로 재조회하며(#9), 친구에게 귀속시키는(#10) 발송자 흐름임. 셋 다 로그인 필수임. 실패는 문구가 아니라 `code`로 분기할 것.")
@RestController
@RequestMapping("/api/v1/recommend")
public class RecommendController {

	private final RecommendService recommendService;

	private final CurrentMember currentMember;

	public RecommendController(RecommendService recommendService, CurrentMember currentMember) {
		this.recommendService = recommendService;
		this.currentMember = currentMember;
	}

	/**
	 * 예산 단위는 만원임. relation이 비면 친구로 둠 — 화면 기본 선택값과 같음.
	 *
	 * friendId는 선택임. 오면 그 친구의 취향을 점수에 반영하고, 없으면 이 필드가 생기기 전과 결과가 같음.
	 */
	public record RecommendRequest(@Schema(
			description = "관계 태그. `연인`·`친구`·`부모님`·`스승/제자` 넷 중 하나임. 그 밖의 값은 `REC400_4`임. 비우면 `친구`로 둠(화면 기본 선택값과 같음)",
			example = "연인", allowableValues = {
					"연인", "친구", "부모님", "스승/제자" },
			requiredMode = Schema.RequiredMode.NOT_REQUIRED) String relation,
			@Schema(description = "최소 예산. **단위는 만원임** — `50`이 50만원임. 원 단위로 보내면 결과가 비거나 엉뚱해짐. 음수는 `REC400_4`임. 비우면 `0`으로 둠",
					example = "50", requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer minBudget,
			@Schema(description = "최대 예산. **단위는 만원임** — `150`이 150만원임. 최소 예산보다 작으면 `REC400_1`임. 음수는 `REC400_4`임. 비우면 `0`으로 둠",
					example = "150", requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer maxBudget,
			@Schema(description = "취향을 반영할 친구 id. **선택임** — 안 보내면 이 필드가 생기기 전과 결과가 완전히 같음. 없거나 남의 친구, 삭제된 친구면 `FRIEND404_1`임(이 필드를 보내는 경우에만 옴)",
					example = "101", requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long friendId){
	}

	public record SaveRequest(@Schema(
			description = "귀속할 친구 id. 같은 친구로 다시 불러도 200이고 행이 늘지 않음. 다른 친구로 바꾸려 하면 `REC400_2`임. 없거나 남의 친구, 삭제된 친구면 `FRIEND404_1`임",
			example = "101", requiredMode = Schema.RequiredMode.REQUIRED) Long friendId) {
	}

	/** FR-009 추천 생성. 회원만 쓰는 발송자 흐름임. 응답에 recommendationId가 붙되 results 형태는 불변임. */
	@Operation(summary = "추천 생성 (#8)",
			description = """
					관계 태그와 예산으로 선물을 추천함. 로그인 필수임. 호출마다 이력이 남음.

					함정 1 — **예산 단위는 만원임**(`minBudget: 50`은 50만원임). 원 단위로 보내면 결과가 비거나 엉뚱해짐.

					함정 2 — `relation`은 `연인`·`친구`·`부모님`·`스승/제자` 넷뿐이고 그 밖은 `REC400_4`임. 비우면 `친구`로 둠(화면 기본 선택값과 같음).

					함정 3 — `friendId`는 선택임. 오면 그 친구의 취향(`taste_profile`)을 점수에 반영하고, 안 보내면 이 필드가 생기기 전과 결과가 완전히 같음. 읽는 키는 복수형 `colors`·`styles`와 단수형 `color`·`style` 넷뿐이고 가방 디자인은 안 읽음. 다중 선택은 축당 한 번만 가산함.

					함정 4 — `reasonType`은 `PERSONAL`과 `GENERAL` 둘이고 **첫 항목만 PERSONAL이 될 수 있음**(ADR-009).

					함정 5 — `FRIEND404_1`은 `friendId`를 보내는 경우에만 옴.
					""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "추천 생성 성공",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "성공",
									value = """
											{
											  "isSuccess": true,
											  "code": "200",
											  "message": "OK",
											  "result": {
											    "recommendationId": 3,
											    "results": [
											      {
											        "product": { "id": 1, "name": "Tracy 비세토스 크로스바디", "price": 890000, "color": "블랙", "imageUrl": "https://images.mcmworldwide.com/i/mcmworldwide/MMRGATA07BK001_01/MMRGATA07BK001?$large$&fmt=auto&qlt=default" },
											        "reasonType": "PERSONAL",
											        "reason": "블랙 계열을 좋아하신다고 하셔서 골랐어요"
											      }
											    ]
											  }
											}
											"""))),
			@ApiResponse(responseCode = "400", description = "`REC400_1` 예산 역전, `REC400_4` `relation`이 4종 밖이거나 예산이 음수",
					content = @Content(mediaType = "application/json",
							examples = {
									@ExampleObject(name = "REC400_1 예산 역전",
											value = """
													{ "isSuccess": false, "code": "REC400_1", "message": "최소 예산을 최대 예산 이하로 입력해주세요", "result": null }
													"""),
									@ExampleObject(name = "REC400_4 조건 오류",
											value = """
													{ "isSuccess": false, "code": "REC400_4", "message": "추천 조건을 다시 선택해주세요", "result": null }
													""") })),
			@ApiResponse(responseCode = "401", description = "`AUTH401_1` 세션 없음",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1", value = """
									{ "isSuccess": false, "code": "AUTH401_1", "message": "로그인이 필요합니다", "result": null }
									"""))),
			@ApiResponse(responseCode = "404", description = "`FRIEND404_1` 없거나 남의 친구, 삭제된 친구 — `friendId`를 보낸 경우에만 옴",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "FRIEND404_1",
									value = """
											{ "isSuccess": false, "code": "FRIEND404_1", "message": "친구 정보를 찾을 수 없습니다", "result": null }
											"""))) })
	@PostMapping
	public CustomResponse<Map<String, Object>> recommend(@RequestBody RecommendRequest request) {
		RecommendService.Created created = this.recommendService.recommend(this.currentMember.requireId(),
				(request.relation() == null) ? "친구" : request.relation(),
				(request.minBudget() == null) ? 0 : request.minBudget(),
				(request.maxBudget() == null) ? 0 : request.maxBudget(), request.friendId());

		return CustomResponse.ok(Map.of("recommendationId", created.recommendationId(), "results", created.results()));
	}

	/**
	 * TC-009 스냅샷 재조회임. 동결되는 것은 상품 구성과 순위와 근거이고 상품의 이름과 가격은 현재 값임 — 재계산은 하지 않지만 표시값은 동결하지
	 * 않음.
	 */
	@Operation(summary = "추천 스냅샷 재조회 (#9)",
			description = """
					생성 시점의 추천을 그대로 다시 봄. 로그인 필수임. 재계산하지 않음.

					함정 1 — **동결하는 것은 상품 구성과 순위와 근거뿐임.** 상품의 이름과 가격은 현재 값이라 바뀌면 바뀐 값이 보임.

					함정 2 — 없는 추천과 **남의 추천이 같은 `REC404_1`임.** 존재 여부를 알려주면 남의 추천 ID를 탐색할 수 있어서임.

					함정 3 — 경로 값이 숫자가 아니면(`/api/v1/recommend/abc`) `VALID400_0`임.

					응답 `result`는 `{ "recommendationId", "context", "savedFriendId", "createdAt", "results": [{..., "rankNo": 1}] }` 형태임.
					""")
	@ApiResponses({
			@ApiResponse(responseCode = "400", description = "`VALID400_0` 경로 값이 선언 타입으로 변환 안 됨", content = @Content(
					mediaType = "application/json", examples = @ExampleObject(name = "VALID400_0", value = """
							{ "isSuccess": false, "code": "VALID400_0", "message": "요청 값의 형식을 확인해주세요", "result": null }
							"""))),
			@ApiResponse(responseCode = "401", description = "`AUTH401_1` 세션 없음",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1", value = """
									{ "isSuccess": false, "code": "AUTH401_1", "message": "로그인이 필요합니다", "result": null }
									"""))),
			@ApiResponse(responseCode = "404", description = "`REC404_1` 없는 추천 또는 남의 추천", content = @Content(
					mediaType = "application/json", examples = @ExampleObject(name = "REC404_1", value = """
							{ "isSuccess": false, "code": "REC404_1", "message": "추천 정보를 찾을 수 없습니다", "result": null }
							"""))) })
	@GetMapping("/{id}")
	public CustomResponse<RecommendService.Snapshot> snapshot(@PathVariable Long id) {
		return CustomResponse.ok(this.recommendService.snapshot(this.currentMember.requireId(), id));
	}

	/** FR-011 취향 저장 귀속임. 같은 친구로 다시 불러도 200이고 행이 늘지 않음(TC-011). */
	@Operation(summary = "추천을 친구에게 귀속 (#10)", description = """
			FR-011 취향 저장 귀속임. 로그인 필수임.

			함정 1 — 같은 친구로 다시 불러도 200이고 행이 늘지 않음(TC-011). 다른 친구로 바꾸려 하면 `REC400_2`임.

			함정 2 — **이 경로는 REC 코드만 오지 않음.** `FRIEND404_1`도 함께 처리할 것.

			함정 3 — 없는 추천과 남의 추천이 같은 `REC404_1`임.
			""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "귀속 성공(재호출도 같은 응답임)",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "성공", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": { "ok": true }
									}
									"""))),
			@ApiResponse(responseCode = "400", description = "`REC400_2` 이미 다른 친구에게 귀속된 추천",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "REC400_2",
									value = """
											{ "isSuccess": false, "code": "REC400_2", "message": "이미 다른 친구에게 저장한 추천이라 바꿀 수 없습니다", "result": null }
											"""))),
			@ApiResponse(responseCode = "401", description = "`AUTH401_1` 세션 없음",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1", value = """
									{ "isSuccess": false, "code": "AUTH401_1", "message": "로그인이 필요합니다", "result": null }
									"""))),
			@ApiResponse(responseCode = "404", description = "`REC404_1` 없거나 남의 추천, `FRIEND404_1` 없거나 남의 친구·삭제된 친구",
					content = @Content(mediaType = "application/json",
							examples = {
									@ExampleObject(name = "REC404_1",
											value = """
													{ "isSuccess": false, "code": "REC404_1", "message": "추천 정보를 찾을 수 없습니다", "result": null }
													"""),
									@ExampleObject(name = "FRIEND404_1",
											value = """
													{ "isSuccess": false, "code": "FRIEND404_1", "message": "친구 정보를 찾을 수 없습니다", "result": null }
													""") })) })
	@PostMapping("/{id}/save")
	public CustomResponse<Map<String, Object>> save(@PathVariable Long id, @RequestBody SaveRequest request) {
		this.recommendService.attachToFriend(this.currentMember.requireId(), id, request.friendId());
		return CustomResponse.ok(Map.of("ok", true));
	}

}
