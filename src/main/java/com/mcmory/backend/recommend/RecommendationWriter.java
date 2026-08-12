package com.mcmory.backend.recommend;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 1행과 결과 행들을 한 트랜잭션으로 남기는 것임.
 *
 * **별도 빈인 이유**: 모델 호출이 트랜잭션 밖에 있어야 하기 때문임. `RecommendService.recommend()`에
 * `@Transactional`을 두면 후보 조회로 붙은 DB 커넥션이 Bedrock 왕복(`bedrock.timeout-millis`, 기본 2500ms)
 * 내내 잡혀 있고, 옵트인 호출이 몰리면 커넥션 풀이 말라 추천과 무관한 요청까지 대기함.
 *
 * **같은 클래스 안의 메서드로 나눠서는 안 됨** — 자기 호출은 프록시를 타지 않아 `@Transactional`이 조용히 무효가 됨. 그러면 결과 행이
 * 하나씩 별도 트랜잭션으로 들어가 중간 실패 시 결과 없는 추천 행이 남음.
 */
@Component
class RecommendationWriter {

	private final RecommendationRepository recommendations;

	private final RecommendationResultRepository recommendationResults;

	RecommendationWriter(RecommendationRepository recommendations,
			RecommendationResultRepository recommendationResults) {
		this.recommendations = recommendations;
		this.recommendationResults = recommendationResults;
	}

	/** 저장하고 추천 id를 돌려줌. 순위는 목록 순서 그대로 1부터임. */
	@Transactional
	Long save(Long memberId, String contextJson, List<RecommendService.Result> results) {
		Recommendation saved = this.recommendations.saveAndFlush(new Recommendation(memberId, contextJson));

		for (int index = 0; index < results.size(); index++) {
			RecommendService.Result result = results.get(index);
			this.recommendationResults.save(new RecommendationResult(saved.getId(), result.product().id(), index + 1,
					result.reasonType(), result.reason()));
		}

		return saved.getId();
	}

}
