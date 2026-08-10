package com.mcmory.backend.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쓰기 메서드에 트랜잭션 애노테이션을 개별로 붙임. 호출자(AuthService.reissue)가 NOT_SUPPORTED라 트랜잭션이 없고, 그 상태에서는
 * 수정 질의가 EntityManager를 얻지 못해 500이 남(실측). 문장마다 독립 트랜잭션으로 커밋되는 것이 ADR-013 결정 9의 4항이 요구하는
 * 가시성이기도 함 — 회전과 유예 조회가 한 스냅샷에 묶이면 유예가 무력화됨.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * 원자 회전임. WHERE에 현재 해시와 만료를 함께 걸어 동시에 들어온 두 요청 중 하나만 1을 돌려받게 함(경합 승자 판정). 직전 해시와 회전
	 * 시각을 같은 문장에서 기록해야 패자가 유예 조회로 자신을 찾을 수 있음(ADR-013 결정 9의 2항).
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE RefreshToken r
			   SET r.tokenHash = :newHash, r.prevTokenHash = :oldHash, r.prevRotatedAt = :now
			 WHERE r.tokenHash = :oldHash AND r.expiresAt > :now
			""")
	int rotate(@Param("oldHash") String oldHash, @Param("newHash") String newHash, @Param("now") LocalDateTime now);

	/**
	 * 유예 조회임. 회전에서 진 요청이 자신이 보낸(이미 교체된) 해시로 행을 찾는 경로. 유예창 안이고 행이 아직 유효해야 함(결정 9의 1항과 5항).
	 */
	@Query("""
			SELECT r FROM RefreshToken r
			 WHERE r.prevTokenHash = :oldHash
			   AND r.prevRotatedAt >= :graceFrom
			   AND r.expiresAt > :now
			""")
	Optional<RefreshToken> findWithinGrace(@Param("oldHash") String oldHash,
			@Param("graceFrom") LocalDateTime graceFrom, @Param("now") LocalDateTime now);

	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM RefreshToken r WHERE r.tokenHash = :tokenHash")
	int deleteByTokenHash(@Param("tokenHash") String tokenHash);

}
