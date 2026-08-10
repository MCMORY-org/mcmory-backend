package com.mcmory.backend.member;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ADR-013 결정 1: 전화번호가 로그인 ID이고 자격증명이 member에 함께 있음. 별도 credential 테이블을 두지 않음.
 */
@Entity
@Table(name = "member")
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String name;

	@Column(nullable = false, length = 11, unique = true)
	private String phone;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Column(length = 10)
	private String gender;

	@Column(name = "sms_opt_in", nullable = false)
	private boolean smsOptIn;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected Member() {
	}

	/**
	 * FR-001 가입임. phone은 숫자만, passwordHash는 이미 해싱된 값을 받음 — 이 클래스가 인코더를 알면 엔티티가 보안 정책에 묶임.
	 *
	 * smsOptIn은 consent 이력의 현재값 캐시임. **SMS_OPT_IN consent 행을 남기는 자가 같은 트랜잭션에서 이 값도 쓴다**가
	 * 규칙이고 기존 행 수정은 금지임(ADR-002 append-only). 철회가 생기면 새 행 추가와 캐시 갱신을 함께 한다.
	 */
	public Member(String name, String phone, String passwordHash, LocalDate birthDate, String gender,
			boolean smsOptIn) {
		this.name = name;
		this.phone = phone;
		this.passwordHash = passwordHash;
		this.birthDate = birthDate;
		this.gender = gender;
		this.smsOptIn = smsOptIn;
	}

	public Long getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getPhone() {
		return this.phone;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	/** SMS 동의 이력의 현재값 캐시를 맞춤. 호출자는 같은 트랜잭션에서 consent 행도 남겨야 함(ConsentService). */
	public void updateSmsOptIn(boolean smsOptIn) {
		this.smsOptIn = smsOptIn;
	}

	public boolean isSmsOptIn() {
		return this.smsOptIn;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

}
