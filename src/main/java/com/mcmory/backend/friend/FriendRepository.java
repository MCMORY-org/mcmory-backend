package com.mcmory.backend.friend;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FriendRepository extends JpaRepository<Friend, Long> {

	List<Friend> findByOwnerMemberIdAndDeletedAtIsNullOrderById(Long ownerMemberId);

	Optional<Friend> findByIdAndOwnerMemberIdAndDeletedAtIsNull(Long id, Long ownerMemberId);

	Optional<Friend> findFirstByOwnerMemberIdAndNameAndDeletedAtIsNull(Long ownerMemberId, String name);

	/** 설문 링크 진입임(읽기 전용). */
	Optional<Friend> findBySurveyTokenAndDeletedAtIsNull(String surveyToken);

	/**
	 * 취향을 쓰는 경로는 **전부 이 잠금을 지나야 함.** 친구 행 하나가 설문 제출·발송자 저장·친구 삭제의 직렬화 지점임.
	 *
	 * 잠그지 않으면 동시 제출 둘이 함께 DELETE를 통과하고 INSERT에서 `uq_taste_friend`가 터짐. 그 예외는 잡아도 트랜잭션이
	 * rollback-only라 `COMMON500`으로 샘 — 예외 흡수로는 못 막음(2026-08-11 codex 검토).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT f FROM Friend f WHERE f.surveyToken = :surveyToken AND f.deletedAt IS NULL")
	Optional<Friend> findBySurveyTokenForUpdate(String surveyToken);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT f FROM Friend f WHERE f.id = :id AND f.ownerMemberId = :ownerMemberId AND f.deletedAt IS NULL")
	Optional<Friend> findByIdAndOwnerForUpdate(Long id, Long ownerMemberId);

}
