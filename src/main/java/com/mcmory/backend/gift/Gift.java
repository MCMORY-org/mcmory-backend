package com.mcmory.backend.gift;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ADR-011 상태 전이: CREATED에서 SENT로, 역방향 없음. 프로토타입은 발송 시점에 바로 SENT로 만듦.
 *
 * letter 테이블을 gift에 병합했음 — 선물 1건에 편지 1건이라 분리 이득이 없음.
 */
@Entity
@Table(name = "gift")
public class Gift {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sender_member_id", nullable = false)
	private Long senderMemberId;

	@Column(name = "friend_id", nullable = false)
	private Long friendId;

	@Column(name = "product_id")
	private Long productId;

	@Column(name = "recommendation_id")
	private Long recommendationId;

	@Column(name = "invite_token", nullable = false, length = 64, unique = true)
	private String inviteToken;

	@Column(name = "anon_nickname", nullable = false, length = 30)
	private String anonNickname;

	@Column(name = "is_anonymous", nullable = false)
	private boolean anonymous = true;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "letter_body", columnDefinition = "text")
	private String letterBody;

	@Column(name = "letter_image_urls", columnDefinition = "json")
	private String letterImageUrls;

	/** FR-012 편지지 색상임. `#rrggbb` 소문자로 정규화해 저장하고, 없으면 기본 편지지임. */
	@Column(name = "letter_color", length = 7)
	private String letterColor;

	@Column(name = "change_requested_at")
	private LocalDateTime changeRequestedAt;

	/**
	 * ADR-006 결정 5: 발송 시점에 전화번호로 해석한 회원 수신자임. 조회 때마다 조인하지 않는 이유는 친구 삭제가 전화번호를 즉시
	 * 파기해(ADR-003) 수신자의 수신함이 발송자 행위로 비어버리기 때문임.
	 */
	@Column(name = "recipient_member_id")
	private Long recipientMemberId;

	@Column(name = "recipient_consented_at")
	private LocalDateTime recipientConsentedAt;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	@Column(name = "opened_at")
	private LocalDateTime openedAt;

	protected Gift() {
	}

	public static Gift send(Long senderMemberId, Long friendId, Long recipientMemberId, Long productId,
			Long recommendationId, String inviteToken, String anonNickname, String letterBody, String letterImageUrls,
			String letterColor) {
		Gift gift = new Gift();
		gift.senderMemberId = senderMemberId;
		gift.recipientMemberId = recipientMemberId;
		gift.friendId = friendId;
		gift.productId = productId;
		gift.recommendationId = recommendationId;
		gift.inviteToken = inviteToken;
		gift.anonNickname = anonNickname;
		gift.anonymous = true;
		gift.status = "SENT";
		gift.letterBody = letterBody;
		gift.letterImageUrls = letterImageUrls;
		gift.letterColor = letterColor;
		gift.sentAt = LocalDateTime.now();
		return gift;
	}

	/**
	 * FR-016: 최초 열람만 기록함. 재방문이 동의 시각과 열람 시각을 갈아치우지 않아야 하므로 이미 값이 있으면 유지함.
	 */
	public void consentAndOpen() {
		LocalDateTime now = LocalDateTime.now();
		if (this.recipientConsentedAt == null) {
			this.recipientConsentedAt = now;
		}
		if (this.openedAt == null) {
			this.openedAt = now;
		}
		this.status = "OPENED";
	}

	/**
	 * 수신자를 뒤늦게 귀속함. 발송 시점 전화번호 매칭이 실패한 선물(번호 없는 친구)은 수신자가 실제로 열고 등록할 때가 유일한 인증 지점임 —
	 * 그전까지는 받은 편지함이 영영 비어 있음.
	 *
	 * 이미 다른 회원이 지정된 선물은 호출부가 앞서 막으므로 여기서 덮어쓰지 않음.
	 */
	public void claimRecipient(Long memberId) {
		if (this.recipientMemberId == null) {
			this.recipientMemberId = memberId;
		}
	}

	/** FR-018: 선물당 1회임. 이 시각이 채워져 있으면 이미 문의한 것. */
	public void requestChange() {
		this.changeRequestedAt = LocalDateTime.now();
	}

	public LocalDateTime getChangeRequestedAt() {
		return this.changeRequestedAt;
	}

	public boolean isConsented() {
		return this.recipientConsentedAt != null;
	}

	public Long getId() {
		return this.id;
	}

	public Long getSenderMemberId() {
		return this.senderMemberId;
	}

	public Long getFriendId() {
		return this.friendId;
	}

	public Long getProductId() {
		return this.productId;
	}

	public String getInviteToken() {
		return this.inviteToken;
	}

	public String getAnonNickname() {
		return this.anonNickname;
	}

	public String getStatus() {
		return this.status;
	}

	/** JSON 배열 문자열임. 파싱은 서비스가 함 — 엔티티가 Jackson을 알 필요가 없음. */
	public String getLetterImageUrls() {
		return this.letterImageUrls;
	}

	public String getLetterColor() {
		return this.letterColor;
	}

	public String getLetterBody() {
		return this.letterBody;
	}

	public LocalDateTime getRecipientConsentedAt() {
		return this.recipientConsentedAt;
	}

	public LocalDateTime getOpenedAt() {
		return this.openedAt;
	}

	public LocalDateTime getSentAt() {
		return this.sentAt;
	}

	public Long getRecipientMemberId() {
		return this.recipientMemberId;
	}

}
