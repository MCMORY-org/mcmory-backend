package com.mcmory.backend.owned;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 보유 제품임. source는 GIFT(우리 서비스로 받은 것)와 EXTERNAL(사용자가 등록한 것) 둘임.
 *
 * ADR-004: serialMemo는 **검증값이 아니라 메모**임. 외부 조회 API가 없어 오탈자와 없는 번호를 구별할 수단이 없으므로 화면과 코드
 * 어디에도 인증이나 정품이라는 표현을 두지 않음(NFR-004).
 *
 * 삭제는 soft delete임 — 예약이 FK로 참조하므로 행을 지우면 예약 기록이 끊김.
 */
@Entity
@Table(name = "owned_product")
public class OwnedProduct {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(nullable = false, length = 10)
	private String source;

	@Column(name = "product_id")
	private Long productId;

	@Column(name = "gift_id")
	private Long giftId;

	@Column(name = "custom_name", length = 100)
	private String customName;

	@Column(name = "custom_category", length = 30)
	private String customCategory;

	@Column(name = "custom_color", length = 30)
	private String customColor;

	@Column(name = "serial_memo", length = 100)
	private String serialMemo;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected OwnedProduct() {
	}

	public static OwnedProduct external(Long memberId, Long productId, String serialMemo) {
		OwnedProduct owned = new OwnedProduct();
		owned.memberId = memberId;
		owned.source = "EXTERNAL";
		owned.productId = productId;
		owned.serialMemo = serialMemo;
		return owned;
	}

	/** FR-020: Unwrap에서 받은 선물을 그대로 보유 제품으로 올림. 시리얼이 없는 대신 gift_id가 출처를 가리킴. */
	public static OwnedProduct fromGift(Long memberId, Long productId, Long giftId) {
		OwnedProduct owned = new OwnedProduct();
		owned.memberId = memberId;
		owned.source = "GIFT";
		owned.productId = productId;
		owned.giftId = giftId;
		return owned;
	}

	public void delete() {
		this.deletedAt = LocalDateTime.now();
	}

	public Long getId() {
		return this.id;
	}

	public Long getMemberId() {
		return this.memberId;
	}

	public String getSource() {
		return this.source;
	}

	public Long getProductId() {
		return this.productId;
	}

	public String getSerialMemo() {
		return this.serialMemo;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

}
