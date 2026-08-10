package com.mcmory.backend.recommend;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationResultRepository extends JpaRepository<RecommendationResult, Long> {

	List<RecommendationResult> findByRecommendationIdOrderByRankNo(Long recommendationId);

}
