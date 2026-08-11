package com.mcmory.backend.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 시연용 시드 카탈로그임. demo_serial은 FR-028 매칭 키이고 실서비스 의미가 없음(ADR-004).
 *
 * styleTags는 JSON 배열 컬럼을 문자열로 받음 — 추천 규칙이 태그 포함 여부만 보므로 매퍼를 붙일 이유가 없음.
 */
@Entity
@Table(name = "product")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 30)
	private String category;

	@Column(length = 30)
	private String color;

	private Integer price;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(name = "official_url", length = 500)
	private String officialUrl;

	@Column(name = "style_tags", columnDefinition = "json")
	private String styleTags;

	@Column(name = "demo_serial", length = 30, unique = true)
	private String demoSerial;

	protected Product() {
	}

	public Long getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getColor() {
		return this.color;
	}

	/** FR-024 관리 방법의 키임(ERD 주석). 시드에는 가방, 지갑, 가죽 소품 셋이 있음. */
	public String getCategory() {
		return this.category;
	}

	public String getOfficialUrl() {
		return this.officialUrl;
	}

	public Integer getPrice() {
		return this.price;
	}

	public String getImageUrl() {
		return this.imageUrl;
	}

	public String getStyleTags() {
		return this.styleTags;
	}

}
