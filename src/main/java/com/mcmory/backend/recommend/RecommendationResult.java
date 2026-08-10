package com.mcmory.backend.recommend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 추천 결과 한 줄임. 스냅샷이 동결하는 것은 **상품 구성과 순위와 근거**이고, 상품의 표시 정보(이름, 가격, 이모지)는 product 테이블의 현재
 * 값임 — 가격이 바뀌면 바뀐 값이 보임. 표시값까지 동결하려면 사본 컬럼이 필요한데 시연에 가격 변경 시나리오가 없어 비용만 늘어남.
 */
@Entity
@Table(name = "recommendation_result")
public class RecommendationResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "recommendation_id", nullable = false)
	private Long recommendationId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "rank_no", nullable = false)
	private int rankNo;

	@Column(name = "reason_type", nullable = false, length = 10)
	private String reasonType;

	@Column(length = 200)
	private String reason;

	protected RecommendationResult() {
	}

	public RecommendationResult(Long recommendationId, Long productId, int rankNo, String reasonType, String reason) {
		this.recommendationId = recommendationId;
		this.productId = productId;
		this.rankNo = rankNo;
		this.reasonType = reasonType;
		this.reason = reason;
	}

	public Long getId() {
		return this.id;
	}

	public Long getProductId() {
		return this.productId;
	}

	public int getRankNo() {
		return this.rankNo;
	}

	public String getReasonType() {
		return this.reasonType;
	}

	public String getReason() {
		return this.reason;
	}

}
