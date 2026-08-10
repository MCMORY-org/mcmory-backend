package com.mcmory.backend.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ADR-013 결정 11: 기기(토큰)당 1행임. member_id에 유니크를 걸지 않는 것이 핵심 — campus는 회원당 1행이라 뒤에 로그인한 기기가
 * 앞 기기를 끊었고, PC와 앱을 함께 쓰는 시연에서 세션이 죽었음.
 *
 * 결정 9의 2항: prevTokenHash와 prevRotatedAt이 회전 경쟁 유예창 판정용임. 회전 UPDATE 한 문장에서 함께 기록함.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "prev_token_hash")
	private String prevTokenHash;

	@Column(name = "prev_rotated_at")
	private LocalDateTime prevRotatedAt;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected RefreshToken() {
	}

	public RefreshToken(Long memberId, String tokenHash, LocalDateTime expiresAt) {
		this.memberId = memberId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public Long getId() {
		return this.id;
	}

	public Long getMemberId() {
		return this.memberId;
	}

	public String getTokenHash() {
		return this.tokenHash;
	}

}
