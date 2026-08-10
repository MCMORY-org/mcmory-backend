package com.mcmory.backend.reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreReservationRepository extends JpaRepository<StoreReservation, Long> {

	List<StoreReservation> findByStoreIdAndReserveDate(Long storeId, LocalDate reserveDate);

	List<StoreReservation> findByMemberIdOrderByReserveDateAscTimeSlotAsc(Long memberId);

	Optional<StoreReservation> findByIdAndMemberId(Long id, Long memberId);

}
