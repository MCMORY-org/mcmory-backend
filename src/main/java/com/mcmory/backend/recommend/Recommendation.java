package com.mcmory.backend.recommend;

import java.time.LocalDateTime;

import com.mcmory.backend.global.apiPayload.code.RecommendErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 추천 세션임. 생성 시점에 1행을 남기고 결과는 recommendation_result에 순위대로 붙임(ADR-009 결정 4).
 *
 * friendId는 취향 저장 시 귀속됨. 귀속이 UPDATE라 몇 번을 호출해도 행이 늘지 않음 — 멱등을 별도 제약이 아니라 구조가 강제함(TC-011).
 */
@Entity
@Table(name = "recommendation")
public class Recommendation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "friend_id")
	private Long friendId;

	@Column(nullable = false, columnDefinition = "json")
	private String context;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Recommendation() {
	}

	public Recommendation(Long memberId, String contextJson) {
		this.memberId = memberId;
		this.context = contextJson;
	}

	/**
	 * FR-011 귀속임. 같은 친구로 다시 부르면 아무것도 바꾸지 않고 통과시킴(ADR-009 결정 9의 "같은 세션 중복 저장은 무시").
	 *
	 * 다른 친구로의 재귀속은 거부함 — 세션 1건은 친구 1명에 귀속이 잠정 규칙임. 기획이 "저장 대상을 바꿀 수 있어야 한다"고 답하면 이 분기만
	 * 덮어쓰기로 바꿈.
	 */
	public void attachTo(Long targetFriendId) {
		if (this.friendId != null && !this.friendId.equals(targetFriendId)) {
			throw new CustomException(RecommendErrorCode.ALREADY_ATTACHED);
		}
		this.friendId = targetFriendId;
	}

	public boolean isOwnedBy(Long candidateMemberId) {
		return this.memberId.equals(candidateMemberId);
	}

	public Long getId() {
		return this.id;
	}

	public Long getMemberId() {
		return this.memberId;
	}

	public Long getFriendId() {
		return this.friendId;
	}

	public String getContext() {
		return this.context;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

}
