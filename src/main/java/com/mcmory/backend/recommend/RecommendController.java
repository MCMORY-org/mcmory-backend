package com.mcmory.backend.recommend;

import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
	public record RecommendRequest(String relation, Integer minBudget, Integer maxBudget, Long friendId) {
	}

	public record SaveRequest(Long friendId) {
	}

	/** FR-009 추천 생성. 회원만 쓰는 발송자 흐름임. 응답에 recommendationId가 붙되 results 형태는 불변임. */
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
	@GetMapping("/{id}")
	public CustomResponse<RecommendService.Snapshot> snapshot(@PathVariable Long id) {
		return CustomResponse.ok(this.recommendService.snapshot(this.currentMember.requireId(), id));
	}

	/** FR-011 취향 저장 귀속임. 같은 친구로 다시 불러도 200이고 행이 늘지 않음(TC-011). */
	@PostMapping("/{id}/save")
	public CustomResponse<Map<String, Object>> save(@PathVariable Long id, @RequestBody SaveRequest request) {
		this.recommendService.attachToFriend(this.currentMember.requireId(), id, request.friendId());
		return CustomResponse.ok(Map.of("ok", true));
	}

}
