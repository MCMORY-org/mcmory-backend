package com.mcmory.backend.gift;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GiftRepository extends JpaRepository<Gift, Long> {

	Optional<Gift> findByInviteToken(String inviteToken);

	/**
	 * FR-020: 더블클릭으로 보유 제품이 두 줄 생기는 것을 행 잠금으로 막음. gift_id 유니크 제약을 쓰지 않는 이유는 리포지토리 주석 참고.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT g FROM Gift g WHERE g.inviteToken = :inviteToken")
	Optional<Gift> findByInviteTokenForUpdate(String inviteToken);

	List<Gift> findBySenderMemberIdOrderByIdDesc(Long senderMemberId);

	List<Gift> findByRecipientMemberIdOrderByIdDesc(Long recipientMemberId);

	/**
	 * FIX-W006: 인증된 수신자의 편지 열람임. 소유 조건을 쿼리에 담는 이유는 남의 행을 먼저 읽고 자바에서 버리지 않기 위함이고, 인가 조건을
	 * 빠뜨리기 어렵게 하기 위함임.
	 */
	Optional<Gift> findByIdAndRecipientMemberId(Long id, Long recipientMemberId);

	/** ADR-001: 닉네임은 발송 시 고정 발급이고 이미 쓰인 것을 피함. */
	@Query("SELECT g.anonNickname FROM Gift g")
	List<String> findAllNicknames();

}
