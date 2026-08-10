package com.mcmory.backend.taste;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ADR-009 취향 이원화. 친구당 1행이고 재저장은 덮어쓰기임.
 *
 * source는 OWNER_INPUT(발송자 대리)과 INVITE_ANSWER(수신자 본인) 둘이고 후자는 아직 시드로만 들어옴. memberId와
 * friendId 중 정확히 하나만 값이 있어야 함 — 스키마의 CHECK 제약이 최종 방어선임.
 */
@Entity
@Table(name = "taste_profile")
public class TasteProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id")
	private Long memberId;

	@Column(name = "friend_id")
	private Long friendId;

	@Column(nullable = false, length = 20)
	private String source;

	@Column(nullable = false, columnDefinition = "json")
	private String answers;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected TasteProfile() {
	}

	public static TasteProfile forFriend(Long friendId, String answersJson) {
		TasteProfile profile = new TasteProfile();
		profile.friendId = friendId;
		profile.source = "OWNER_INPUT";
		profile.answers = answersJson;
		return profile;
	}

	public Long getId() {
		return this.id;
	}

	public Long getFriendId() {
		return this.friendId;
	}

	public String getAnswers() {
		return this.answers;
	}

	/**
	 * 수신자 본인이 답한 취향인지임(ADR-009 결정 2의 INVITE_ANSWER). 발송자 대리 입력이 이것을 덮지 못하게 하는 판정에 씀.
	 *
	 * 시드로만 들어오는 값임 — 설문 왕복(Start-01, Start-02)은 아직 범위 밖이라 이 값을 쓰는 API 경로가 없음.
	 */
	public boolean isFromInvite() {
		return "INVITE_ANSWER".equals(this.source);
	}

}
