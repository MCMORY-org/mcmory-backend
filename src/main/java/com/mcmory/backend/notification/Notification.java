package com.mcmory.backend.notification;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** FR-016: 수신자가 최초 열람하면 발송자에게 알림이 생김. */
@Entity
@Table(name = "notification")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(nullable = false, length = 30)
	private String type;

	@Column(name = "gift_id")
	private Long giftId;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Notification() {
	}

	/** FR-017 도착 알림임. 발송 시점에 회원 수신자가 해석된 경우에만 만듦. */
	public static Notification giftArrived(Long memberId, Long giftId) {
		return of(memberId, "GIFT_ARRIVED", giftId);
	}

	/**
	 * FR-018 옵션 변경 문의임. 사유가 고정 4종이라 별도 컬럼 없이 타입에 실음 — `CHANGE_REQ_SIZE` 형태. 사유를 담을 컬럼을 새로
	 * 만들면 DDL 두 벌과 기동 중 DB를 함께 고쳐야 하는데 값에 비해 비쌈.
	 */
	public static Notification changeRequested(Long memberId, Long giftId, String reason) {
		return of(memberId, "CHANGE_REQ_" + reason, giftId);
	}

	public void markRead() {
		if (this.readAt == null) {
			this.readAt = LocalDateTime.now();
		}
	}

	private static Notification of(Long memberId, String type, Long giftId) {
		Notification notification = new Notification();
		notification.memberId = memberId;
		notification.type = type;
		notification.giftId = giftId;
		return notification;
	}

	public static Notification giftOpened(Long memberId, Long giftId) {
		Notification notification = new Notification();
		notification.memberId = memberId;
		notification.type = "GIFT_OPENED";
		notification.giftId = giftId;
		return notification;
	}

	public Long getId() {
		return this.id;
	}

	public String getType() {
		return this.type;
	}

	public Long getGiftId() {
		return this.giftId;
	}

	public LocalDateTime getReadAt() {
		return this.readAt;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

}
