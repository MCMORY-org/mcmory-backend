package com.mcmory.backend.product;

import java.util.Set;

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
	/**
	 * 선물로 보낼 수 있는 카테고리임. 시드에 코디용 의류(WOMAN OUTER, WOMAN TOP, WOMAN BOTTOM)가 들어오면서 생긴 구분임 —
	 * 의류는 FR-023 스타일링 제안 대상이지 선물 대상이 아님.
	 *
	 * 컬럼을 새로 만들지 않고 카테고리로 판정하는 이유는 스키마 변경을 피하기 위함임. 카테고리가 늘면 여기를 고칠 것.
	 */
	private static final Set<String> GIFT_CATEGORIES = Set.of("가방", "지갑", "가죽 소품");

	/**
	 * 선물 추천과 발송의 적격 판정임. **두 경로가 이 하나를 함께 봄** — 추천만 막으면 productId를 직접 실은 발송으로 뚫림.
	 *
	 * 사진이 없는 상품은 제외함. 화면이 빈 자리를 그리게 되는데, 선물을 고르는 화면에서 그것은 상품이 없는 것처럼 보임.
	 */
	public boolean isGiftEligible() {
		return GIFT_CATEGORIES.contains(this.category) && this.imageUrl != null;
	}

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
