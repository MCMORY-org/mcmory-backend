package com.mcmory.backend.taste;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ADR-009 취향 이원화. 프로토타입은 source가 OWNER_INPUT뿐이고 친구당 1행임(재저장은 덮어쓰기).
 *
 * memberId와 friendId 중 정확히 하나만 값이 있어야 함 — 스키마의 CHECK 제약이 최종 방어선임.
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

}
