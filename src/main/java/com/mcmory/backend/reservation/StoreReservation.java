package com.mcmory.backend.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ADR-012: 취소는 **행 삭제**로 슬롯을 해제함. MySQL에는 부분 유니크 인덱스가 없어 "활성 예약에만 걸리는 조건부 유니크"를 만들 수 없고,
 * 상태 컬럼으로 취소를 표현하면 유니크 제약이 취소된 행까지 붙잡아 같은 슬롯을 영구히 막음.
 *
 * UNIQUE(store_id, reserve_date, time_slot)이 슬롯 선점의 최종 방어선임 — 조회로 먼저 막되 경쟁에서 진 요청은 제약이
 * 잡음.
 */
@Entity
@Table(name = "store_reservation")
public class StoreReservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Column(name = "owned_product_id", nullable = false)
	private Long ownedProductId;

	@Column(name = "reserve_date", nullable = false)
	private LocalDate reserveDate;

	@Column(name = "time_slot", nullable = false, length = 5)
	private String timeSlot;

	@Column(name = "request_note", length = 500)
	private String requestNote;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected StoreReservation() {
	}

	public StoreReservation(Long memberId, Long storeId, Long ownedProductId, LocalDate reserveDate, String timeSlot,
			String requestNote) {
		this.memberId = memberId;
		this.storeId = storeId;
		this.ownedProductId = ownedProductId;
		this.reserveDate = reserveDate;
		this.timeSlot = timeSlot;
		this.requestNote = requestNote;
	}

	public Long getId() {
		return this.id;
	}

	public Long getStoreId() {
		return this.storeId;
	}

	public Long getOwnedProductId() {
		return this.ownedProductId;
	}

	public LocalDate getReserveDate() {
		return this.reserveDate;
	}

	public String getTimeSlot() {
		return this.timeSlot;
	}

	public String getRequestNote() {
		return this.requestNote;
	}

	/** 방문 시각임. 취소 가능 여부(1시간 전까지) 판정에 씀. */
	public LocalDateTime visitAt() {
		return this.reserveDate.atTime(java.time.LocalTime.parse(this.timeSlot));
	}

}
