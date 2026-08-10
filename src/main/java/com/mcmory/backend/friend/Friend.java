package com.mcmory.backend.friend;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ADR-003: soft delete이되 개인정보 필드는 **즉시 파기**함(지연 없음). name과 phone이 nullable인 이유가 그것임.
 *
 * gift가 friend를 FK로 참조하므로 행 자체는 남겨야 함 — 지우면 선물 기록이 끊김.
 */
@Entity
@Table(name = "friend")
public class Friend {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_member_id", nullable = false)
	private Long ownerMemberId;

	@Column(length = 20)
	private String name;

	@Column(length = 11)
	private String phone;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected Friend() {
	}

	public Friend(Long ownerMemberId, String name, String phone) {
		this.ownerMemberId = ownerMemberId;
		this.name = name;
		this.phone = phone;
	}

	/**
	 * ADR-003: 개인정보 필드를 즉시 파기함. **이름도 NULL이다** — 표시 문구를 컬럼에 써 넣으면 그건 파기가 아니라 덮어쓰기이고, 저장된
	 * 값이 개인정보가 아니게 됐을 뿐 "지웠다"고 말할 수 없음(TC-028이 두 컬럼 모두 NULL을 요구함).
	 */
	/** FR-005: 이름만 바꿈. 전화번호 setter를 두지 않는 것이 "번호가 바뀌면 다른 사람" 규칙의 강제 지점임. */
	public void rename(String name) {
		this.name = name;
	}

	public void erase() {
		this.deletedAt = LocalDateTime.now();
		this.name = null;
		this.phone = null;
	}

	/** 화면에 보일 이름임. 파기된 친구는 컬럼이 NULL이라 표시 시점에 문구로 바꿈. */
	public String displayName() {
		return (this.name == null) ? "삭제된 친구" : this.name;
	}

	public Long getId() {
		return this.id;
	}

	public Long getOwnerMemberId() {
		return this.ownerMemberId;
	}

	public String getName() {
		return this.name;
	}

	public String getPhone() {
		return this.phone;
	}

	public LocalDateTime getDeletedAt() {
		return this.deletedAt;
	}

}
