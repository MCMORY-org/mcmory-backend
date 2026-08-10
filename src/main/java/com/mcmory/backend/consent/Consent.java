package com.mcmory.backend.consent;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ADR-002: 동의 이력은 append-only임. 철회도 새 행으로 남기고 기존 행을 고치지 않음.
 *
 * memberId와 giftId 중 정확히 하나만 값이 있어야 함(스키마 CHECK). 수신자 동의는 회원이 아닐 수 있어 giftId 쪽에 남김.
 */
@Entity
@Table(name = "consent")
public class Consent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id")
	private Long memberId;

	@Column(name = "gift_id")
	private Long giftId;

	@Column(name = "consent_type", nullable = false, length = 30)
	private String consentType;

	@Column(nullable = false)
	private boolean agreed;

	@Column(name = "terms_version", nullable = false, length = 20)
	private String termsVersion = "v1";

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Consent() {
	}

	/**
	 * 회원 동의임. 동의 시점의 버전을 그 행에 박음 — 나중에 버전이 올라가면 이 값과 비교해 재동의 필요를 판정함.
	 *
	 * gift_id를 만지는 경로를 아예 두지 않는 것이 스키마 CHECK(member_id와 gift_id 중 하나만)의 방어선임.
	 */
	public static Consent ofMember(Long memberId, ConsentType type, boolean agreed) {
		Consent consent = new Consent();
		consent.memberId = memberId;
		consent.consentType = type.name();
		consent.agreed = agreed;
		consent.termsVersion = type.currentVersion();
		return consent;
	}

	public static Consent recipientPrivacy(Long giftId) {
		Consent consent = new Consent();
		consent.giftId = giftId;
		consent.consentType = ConsentType.RECIPIENT_PRIVACY.name();
		consent.agreed = true;
		consent.termsVersion = ConsentType.RECIPIENT_PRIVACY.currentVersion();
		return consent;
	}

	public Long getId() {
		return this.id;
	}

	public Long getGiftId() {
		return this.giftId;
	}

	public Long getMemberId() {
		return this.memberId;
	}

	public String getConsentType() {
		return this.consentType;
	}

	public boolean isAgreed() {
		return this.agreed;
	}

	public String getTermsVersion() {
		return this.termsVersion;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

}
