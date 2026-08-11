package com.mcmory.backend.styling;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mcmory.backend.global.apiPayload.code.OwnedProductErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.owned.OwnedProduct;
import com.mcmory.backend.owned.OwnedProductRepository;
import com.mcmory.backend.product.Product;
import com.mcmory.backend.product.ProductRepository;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * FR-023 AI 스타일링임. 보유 제품 하나에 어울리는 제품 3건과 근거 문구를 줌(화면 CONTINUE-02).
 *
 * **후보 3건은 이 코드가 고르고 LLM은 첫 근거 문구만 씀.** 상품 선택을 모델에 맡기면 카탈로그에 없는 상품을 지어낼 수 있고 그건 화면에 그대로
 * 나가는 계약 오염임. 문구만 받으면 환각이 상품 목록을 못 건드림.
 *
 * ADR-009에 따라 **첫 항목만 PERSONAL**이고 나머지 둘은 고정 GENERAL 문구임. 그래서 LLM 호출도 요청당 1회임.
 */
@Service
public class StylingService {

	/** 화면이 코디를 그리므로 의류를 먼저 채움. 안 그러면 지갑과 가죽 소품이 동점에서 항상 이겨 코디가 안 나옴. */
	private static final Set<String> CLOTHING_CATEGORIES = Set.of("WOMAN OUTER", "WOMAN TOP", "WOMAN BOTTOM");

	private static final String GENERAL_REASON = "모델이 함께 매치한 제품이에요";

	private static final int RESULT_SIZE = 3;

	private final OwnedProductRepository owned;

	private final ProductRepository products;

	private final StylingReasonWriter reasonWriter;

	private final ObjectMapper objectMapper;

	public StylingService(OwnedProductRepository owned, ProductRepository products, StylingReasonWriter reasonWriter,
			ObjectMapper objectMapper) {
		this.owned = owned;
		this.products = products;
		this.reasonWriter = reasonWriter;
		this.objectMapper = objectMapper;
	}

	@Schema(name = "StylingOwnedProduct", description = "스타일링 기준이 되는 보유 제품임.")
	public record OwnedView(
			@Schema(description = "상품 id임. 화면은 이 값으로 상품 이미지를 고름", example = "4",
					requiredMode = Schema.RequiredMode.REQUIRED) Long productId,
			@Schema(description = "상품명임", example = "미니 Aren 비세토스 카드 케이스",
					requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "상품 카테고리임", example = "가죽 소품",
					requiredMode = Schema.RequiredMode.REQUIRED) String category) {
	}

	@Schema(name = "StylingSuggestion", description = "스타일링 추천 한 건임.")
	public record Suggestion(
			@Schema(description = "제안 상품 id임. 화면은 이 값으로 상품 이미지를 고름", example = "11",
					requiredMode = Schema.RequiredMode.REQUIRED) Long productId,
			@Schema(description = "상품명임", example = "워싱 데님 재킷",
					requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "상품 카테고리임", example = "WOMAN OUTER",
					requiredMode = Schema.RequiredMode.REQUIRED) String category,
			@Schema(description = "가격임. **단위는 원임**. 값이 없는 상품은 `0`으로 내려감", example = "1250000",
					requiredMode = Schema.RequiredMode.REQUIRED) int price,
			@Schema(description = "공식몰 링크임. `보러가기`가 새 탭으로 엶. 선택 — 상품에 링크가 없으면 `null`임",
					example = "https://kr.mcmworldwide.com",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) String officialUrl,
			@Schema(description = "근거 유형임. **첫 항목만 `PERSONAL`이 될 수 있음**(ADR-009)", example = "PERSONAL",
					allowableValues = {
							"PERSONAL", "GENERAL" },
					requiredMode = Schema.RequiredMode.REQUIRED) String reasonType,
			@Schema(description = "근거 문구임. `GENERAL`이면 고정 문구임", example = "평소 미니멀 스타일을 즐기시네요",
					requiredMode = Schema.RequiredMode.REQUIRED) String reason){
	}

	@Schema(name = "StylingResult", description = "FR-023 AI 스타일링 응답임(화면 CONTINUE-02).")
	public record Result(
			@Schema(description = "스타일링 기준이 된 보유 제품임", requiredMode = Schema.RequiredMode.REQUIRED) OwnedView product,
			@Schema(description = "**첫 근거 문구를 무엇이 썼는지임.** `LLM`이면 모델이 쓴 것이고 `RULE`이면 규칙 문구로 폴백한 것임. "
					+ "**상품 선정은 어느 쪽이든 항상 규칙임** — 그래서 이름이 `source`가 아님. " + "화면이 AI라고 단정하는 문구는 `LLM`일 때만 쓸 것",
					example = "LLM", allowableValues = {
							"LLM", "RULE" },
					requiredMode = Schema.RequiredMode.REQUIRED) String reasonSource,
			@Schema(description = "추천 3건임. 후보가 모자라면 있는 만큼만 옴",
					requiredMode = Schema.RequiredMode.REQUIRED) List<Suggestion> results){
	}

	@Transactional(readOnly = true)
	public Result styling(Long memberId, Long ownedId) {
		if (ownedId == null) {
			throw new CustomException(OwnedProductErrorCode.NOT_FOUND);
		}
		OwnedProduct target = this.owned.findByIdAndMemberIdAndDeletedAtIsNull(ownedId, memberId)
			.orElseThrow(() -> new CustomException(OwnedProductErrorCode.NOT_FOUND));

		// 없는 제품과 남의 제품과 상품 연결이 끊긴 제품을 같은 코드로 다룸 — 관리 방법(#32)과 같은 계약임
		Product base = (target.getProductId() == null) ? null
				: this.products.findById(target.getProductId()).orElse(null);
		if (base == null) {
			throw new CustomException(OwnedProductErrorCode.NOT_FOUND);
		}

		List<Product> picked = pick(base);
		if (picked.isEmpty()) {
			return new Result(viewOf(base), "RULE", List.of());
		}

		List<String> baseTags = readTags(base.getStyleTags());
		String matchedTag = matchedTag(baseTags, readTags(picked.get(0).getStyleTags()));

		String written = this.reasonWriter.write(base, picked.get(0), matchedTag);
		String personalReason = (written == null) ? ruleReason(matchedTag) : written;

		List<Suggestion> results = new ArrayList<>();
		for (int rank = 0; rank < picked.size(); rank++) {
			Product product = picked.get(rank);
			boolean personal = rank == 0;
			results.add(new Suggestion(product.getId(), product.getName(), product.getCategory(),
					(product.getPrice() == null) ? 0 : product.getPrice(), product.getOfficialUrl(),
					personal ? "PERSONAL" : "GENERAL", personal ? personalReason : GENERAL_REASON));
		}

		return new Result(viewOf(base), (written == null) ? "RULE" : "LLM", results);
	}

	/**
	 * 후보 선정임. 규칙 셋을 순서대로 적용함.
	 *
	 * 1. 보유 제품과 **카테고리가 겹치지 않는** 것만 본다(기능명세서의 `제품군 겹치지 않게`) 2. 스타일 태그 교집합이 큰 순, 동점이면 id 순
	 * 3. **의류를 먼저 채우고** 모자랄 때만 비의류로 메운다. 그리고 **결과끼리도 카테고리를 겹치지 않게** 함 — 안 하면 상의만 셋 나옴
	 */
	private List<Product> pick(Product base) {
		List<String> baseTags = readTags(base.getStyleTags());

		List<Product> candidates = new ArrayList<>(this.products.findAllByOrderById()
			.stream()
			.filter((product) -> !product.getId().equals(base.getId()))
			.filter((product) -> !product.getCategory().equals(base.getCategory()))
			.toList());

		// 안정 정렬이라 점수가 같으면 id 순서가 유지됨
		candidates.sort((left, right) -> Integer.compare(overlap(baseTags, readTags(right.getStyleTags())),
				overlap(baseTags, readTags(left.getStyleTags()))));

		List<Product> clothing = candidates.stream().filter(StylingService::isClothing).toList();
		List<Product> rest = candidates.stream().filter((product) -> !isClothing(product)).toList();

		Map<String, Product> byCategory = new LinkedHashMap<>();
		for (Product product : clothing) {
			byCategory.putIfAbsent(product.getCategory(), product);
		}
		for (Product product : rest) {
			byCategory.putIfAbsent(product.getCategory(), product);
		}

		List<Product> picked = new ArrayList<>(byCategory.values().stream().limit(RESULT_SIZE).toList());
		if (picked.size() < RESULT_SIZE) {
			// 카테고리가 모자라면 중복 카테고리로 메움 — 3건을 채우는 것이 화면 요구임
			Set<Long> already = new HashSet<>(picked.stream().map(Product::getId).toList());
			for (Product product : candidates) {
				if (picked.size() >= RESULT_SIZE) {
					break;
				}
				if (already.add(product.getId())) {
					picked.add(product);
				}
			}
		}
		return picked;
	}

	private static boolean isClothing(Product product) {
		return CLOTHING_CATEGORIES.contains(product.getCategory());
	}

	private OwnedView viewOf(Product product) {
		return new OwnedView(product.getId(), product.getName(), product.getCategory());
	}

	private int overlap(List<String> left, List<String> right) {
		return (int) left.stream().filter(right::contains).count();
	}

	/** 근거에 쓸 태그임. 겹치는 것이 없으면 보유 제품의 첫 태그를 씀. */
	private String matchedTag(List<String> baseTags, List<String> pickedTags) {
		return baseTags.stream()
			.filter(pickedTags::contains)
			.findFirst()
			.orElse(baseTags.isEmpty() ? "미니멀" : baseTags.get(0));
	}

	/** LLM이 없거나 실패했을 때 쓰는 문구임. 데모 상수와 같은 형태임. */
	private String ruleReason(String matchedTag) {
		return "평소 " + matchedTag + " 스타일을 즐기시네요";
	}

	private List<String> readTags(String rawJson) {
		if (rawJson == null || rawJson.isBlank()) {
			return List.of();
		}
		List<String> tags = new ArrayList<>();
		for (JsonNode node : this.objectMapper.readTree(rawJson)) {
			tags.add(node.asString());
		}
		return tags;
	}

}
