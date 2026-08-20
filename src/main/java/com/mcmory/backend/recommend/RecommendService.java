package com.mcmory.backend.recommend;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mcmory.backend.global.apiPayload.code.FriendErrorCode;
import com.mcmory.backend.global.apiPayload.code.RecommendErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.friend.FriendRepository;
import com.mcmory.backend.product.Product;
import com.mcmory.backend.product.ProductRepository;
import com.mcmory.backend.taste.TasteProfileRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-009 추천. 규칙이 시드 카탈로그에서 후보를 좁히는 것이 기본이고, 요청이 옵트인하면(`aiReason=true`) 그 후보 안에서만 모델이
 * 고름(FEAT-W004). 모델 응답이 후보를 벗어나거나 깨지면 통째로 버리고 규칙 결과로 돌아감 — 어느 쪽이었는지는 응답 `reasonSource`가
 * 말함.
 *
 * ADR-009: 근거는 PERSONAL과 GENERAL로 이원화하고 **첫 항목만** 개인화 근거를 붙임(디자인 19 실측 반영).
 */
@Service
public class RecommendService {

	/**
	 * 관계별 선호 태그. 예산 필터를 통과한 것 중 이 태그를 가진 상품에 가중치를 줌. 관계를 옷 스타일 태그로 번역해 매칭함. 값은 v1.1 실측
	 * 스타일 6종 안에서만 고름 — 2026-08-10 이전에는 데일리와 실용과 합리적을 썼는데 그건 스타일이 아니라 이 표만을 위해 시드
	 * style_tags에 심어둔 값이라 두 축이 한 컬럼에서 뒤엉켰음(FIX-W001 T2).
	 */
	private static final Map<String, String> RELATION_TAG = Map.of("연인", "러블리", "친구", "캐주얼", "부모님", "클래식", "스승/제자",
			"포멀");

	private static final int RESULT_SIZE = 3;

	/** 프롬프트에 싣는 후보 수임. 3건보다 넓어야 모델이 고를 여지가 생기고, 넓힐수록 환각 면적과 지연이 늚. */
	private static final int CANDIDATE_SIZE = 8;

	private static final String GENERAL_REASON = "모델이 함께 매치한 제품이에요";

	private final ProductRepository products;

	private final RecommendationRepository recommendations;

	private final RecommendationResultRepository recommendationResults;

	private final FriendRepository friends;

	private final TasteProfileRepository tasteProfiles;

	private final ObjectMapper objectMapper;

	private final GiftPicker giftPicker;

	private final RecommendationWriter writer;

	public RecommendService(ProductRepository products, RecommendationRepository recommendations,
			RecommendationResultRepository recommendationResults, FriendRepository friends,
			TasteProfileRepository tasteProfiles, ObjectMapper objectMapper, GiftPicker giftPicker,
			RecommendationWriter writer) {
		this.products = products;
		this.recommendations = recommendations;
		this.recommendationResults = recommendationResults;
		this.friends = friends;
		this.tasteProfiles = tasteProfiles;
		this.objectMapper = objectMapper;
		this.giftPicker = giftPicker;
		this.writer = writer;
	}

	/**
	 * 추천 점수에 쓰는 취향임. 다중 선택이라 목록이고 **비면 취향이 없는 것과 같음.**
	 *
	 * 색상과 스타일 둘만 읽음 — 가방 디자인은 시드 상품에 대응 축이 없음. 채우는 것은 시드와 수신자 설문뿐이고 발송자 대리
	 * 입력(OWNER_INPUT)은 summary만 써서 영향이 없음.
	 */
	private record TasteAnswers(List<String> colors, List<String> styles) {

		static final TasteAnswers NONE = new TasteAnswers(List.of(), List.of());

	}

	public record ProductView(
			@Schema(description = "시드 카탈로그 상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
			@Schema(description = "상품명. 스냅샷 재조회에서도 **현재 값임** — 동결 대상이 아님", example = "Tracy 비세토스 크로스바디",
					requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "가격. **단위는 원임** — 요청 예산의 만원 단위와 다름. 값이 없으면 `0`임. 스냅샷에서도 현재 값임", example = "890000",
					requiredMode = Schema.RequiredMode.REQUIRED) int price,
			@Schema(description = "상품 색상. **취향 색상 6종·편지지 색 4종과 다른 축임** — 시드에 카키·브라운이 있어 6종 밖임. 삭제된 상품이면 `null`임",
					example = "블랙", requiredMode = Schema.RequiredMode.NOT_REQUIRED) String color,
			@Schema(description = "상품 이미지 URL임. **있으면 이 값을 그대로 쓰고, `null`이면 화면이 자체 규약(`/products/{id}.webp`)으로 고를 것.** "
					+ "MCM 공식 CDN 주소라 우리 서버를 거치지 않음. 옛 시드 상품은 `null`이라 화면이 폴백함",
					example = "https://images.mcmworldwide.com/i/mcmworldwide/MMRGATA07BK001_01/MMRGATA07BK001?$large$&fmt=auto&qlt=default",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) String imageUrl) {
	}

	public record Result(
			@Schema(description = "추천된 상품", requiredMode = Schema.RequiredMode.REQUIRED) ProductView product,
			@Schema(description = "근거 유형. `PERSONAL`과 `GENERAL` 둘뿐이고 **첫 항목만 PERSONAL이 될 수 있음**", example = "PERSONAL",
					allowableValues = {
							"PERSONAL", "GENERAL" },
					requiredMode = Schema.RequiredMode.REQUIRED) String reasonType,
			@Schema(description = "근거 문구. `GENERAL`이면 고정 문구임", example = "블랙 계열을 좋아하신다고 하셔서 골랐어요",
					requiredMode = Schema.RequiredMode.REQUIRED) String reason){
	}

	/** 저장된 결과를 읽을 때는 순위를 함께 돌려줌 — 재조회의 요점이 순위 동결이기 때문임. */
	public record StoredResult(
			@Schema(description = "추천된 상품", requiredMode = Schema.RequiredMode.REQUIRED) ProductView product,
			@Schema(description = "근거 유형. `PERSONAL`과 `GENERAL` 둘뿐이고 **첫 항목만 PERSONAL이 될 수 있음**", example = "PERSONAL",
					allowableValues = {
							"PERSONAL", "GENERAL" },
					requiredMode = Schema.RequiredMode.REQUIRED) String reasonType,
			@Schema(description = "근거 문구. 생성 시점 값을 그대로 돌려줌 — 재계산하지 않음", example = "블랙 계열을 좋아하신다고 하셔서 골랐어요",
					requiredMode = Schema.RequiredMode.REQUIRED) String reason,
			@Schema(description = "생성 시점에 동결된 순위. `1`부터 시작하고 오름차순으로 옴", example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED) int rankNo){
	}

	public record Snapshot(
			@Schema(description = "추천 id", example = "3",
					requiredMode = Schema.RequiredMode.REQUIRED) Long recommendationId,
			@Schema(description = "생성 시점의 조건 JSON. 키는 `relation`·`minBudget`·`maxBudget`과, 보냈을 때만 붙는 `friendId`임. "
					+ "**예산 단위는 요청과 같은 만원임**", requiredMode = Schema.RequiredMode.REQUIRED) JsonNode context,
			@Schema(description = "귀속된 친구 id. 아직 귀속하지 않았으면 `null`임 — 귀속은 `POST /api/v1/recommend/{id}/save`가 함",
					example = "101", requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long savedFriendId,
			@Schema(description = "추천 생성 시각", example = "2026-08-11T10:20:30",
					requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
			@Schema(description = "동결된 추천 결과. 순위 오름차순이고 최대 3건임",
					requiredMode = Schema.RequiredMode.REQUIRED) List<StoredResult> results) {
	}

	public record Created(
			@Schema(description = "생성된 추천 id. 재조회(`GET /api/v1/recommend/{id}`)와 선물 발송(`POST /api/v1/gift`)이 이 값을 씀",
					example = "3", requiredMode = Schema.RequiredMode.REQUIRED) Long recommendationId,
			@Schema(description = "**무엇이 3건을 골랐는지임.** `LLM`이면 후보 안에서 모델이 고르고 첫 문구까지 썼다는 뜻이고, `RULE`이면 규칙 결과임. "
					+ "**후보 자체는 어느 쪽이든 항상 규칙이 만듦** — 모델은 그 밖으로 나갈 수 없음. "
					+ "스타일링(`GET /api/v1/owned/{id}/care-guide`)의 같은 이름 필드는 문구 작성 주체만 뜻하므로 의미가 다름. "
					+ "**스냅샷 재조회(`GET /api/v1/recommend/{id}`)에는 저장되지 않음** — 생성 응답 전용임", example = "RULE",
					allowableValues = {
							"LLM", "RULE" },
					requiredMode = Schema.RequiredMode.REQUIRED) String reasonSource,
			@Schema(description = "추천 결과. 최대 3건임. 예산에 맞는 상품이 없으면 전체 카탈로그에서 고름",
					requiredMode = Schema.RequiredMode.REQUIRED) List<Result> results){
	}

	/** 결과와 그것을 무엇이 골랐는지임. 저장은 결과만 하고 출처는 응답에만 실음. */
	private record Calculated(List<Result> results, String reasonSource) {
	}

	/**
	 * FR-009 추천 생성임. 호출마다 recommendation 1행과 결과 행을 남김 — 재조회(TC-009)와 gift 연결이 생성 직후의 ID를
	 * 요구하므로 저장을 귀속 시점으로 미룰 수 없음.
	 */
	public Created recommend(Long memberId, String relation, int minBudgetInTenThousand, int maxBudgetInTenThousand,
			Long friendId, boolean aiReason) {
		TasteAnswers taste = tasteOf(memberId, friendId);
		Calculated calculated = calculate(relation, minBudgetInTenThousand, maxBudgetInTenThousand, taste, aiReason);

		// **이 메서드에 @Transactional을 두지 말 것.** 위 calculate가 Bedrock을 부르므로 트랜잭션 안이면
		// 커넥션이 왕복 내내 잡힘. 저장만 writer가 한 트랜잭션으로 처리함
		Long recommendationId = this.writer.save(memberId,
				toContextJson(relation, minBudgetInTenThousand, maxBudgetInTenThousand, friendId),
				calculated.results());

		return new Created(recommendationId, calculated.reasonSource(), calculated.results());
	}

	/**
	 * TC-009 스냅샷 재조회임. 저장 행을 그대로 돌려주고 재계산하지 않음.
	 *
	 * 남의 추천과 없는 추천을 같은 404로 다룸 — 존재 여부를 알려주면 남의 추천 ID를 탐색할 수 있음.
	 */
	@Transactional(readOnly = true)
	public Snapshot snapshot(Long memberId, Long recommendationId) {
		Recommendation recommendation = this.recommendations.findById(recommendationId)
			.filter((found) -> found.isOwnedBy(memberId))
			.orElseThrow(() -> new CustomException(RecommendErrorCode.NOT_FOUND));

		List<StoredResult> results = this.recommendationResults
			.findByRecommendationIdOrderByRankNo(recommendation.getId())
			.stream()
			.map((row) -> new StoredResult(productViewOf(row.getProductId()), row.getReasonType(), row.getReason(),
					row.getRankNo()))
			.toList();

		return new Snapshot(recommendation.getId(), readTree(recommendation.getContext()), recommendation.getFriendId(),
				recommendation.getCreatedAt(), results);
	}

	/** FR-011 취향 저장 귀속임. UPDATE 한 문장이라 재호출로 행이 늘지 않음(TC-011). */
	@Transactional
	public void attachToFriend(Long memberId, Long recommendationId, Long friendId) {
		Recommendation recommendation = this.recommendations.findById(recommendationId)
			.filter((found) -> found.isOwnedBy(memberId))
			.orElseThrow(() -> new CustomException(RecommendErrorCode.NOT_FOUND));

		this.friends.findByIdAndOwnerMemberIdAndDeletedAtIsNull(friendId, memberId)
			.orElseThrow(() -> new CustomException(FriendErrorCode.NOT_FOUND));

		recommendation.attachTo(friendId);
	}

	/** 소유와 구성 검증임. 선물에 추천을 매달 때 남의 추천 ID와 그 추천에 없는 상품을 막음. */
	@Transactional(readOnly = true)
	public void requireContainsProduct(Long memberId, Long recommendationId, Long productId) {
		this.recommendations.findById(recommendationId)
			.filter((found) -> found.isOwnedBy(memberId))
			.orElseThrow(() -> new CustomException(RecommendErrorCode.INVALID_REFERENCE));

		// 소유만 보면 내 추천 id 하나로 카탈로그의 아무 상품이나 "추천으로 고른 선물"로 실을 수 있음(FIX-W004 ③).
		// 남의 추천과 같은 코드를 씀 — 그 추천이 무엇을 담고 있는지 알려주지 않기 위함임
		boolean contains = this.recommendationResults.findByRecommendationIdOrderByRankNo(recommendationId)
			.stream()
			.anyMatch((row) -> row.getProductId().equals(productId));
		if (!contains) {
			throw new CustomException(RecommendErrorCode.INVALID_REFERENCE);
		}
	}

	/**
	 * FR-009 취향 반영임. friendId가 없으면 NONE을 돌려주므로 이 경로가 없던 때와 결과가 완전히 같음.
	 *
	 * 남의 친구와 삭제된 친구는 취향을 조용히 비우지 않고 404로 막음 — 순번을 훑어 남의 친구 존재 여부를 알아내는 것을 막기 위함임.
	 */
	private TasteAnswers tasteOf(Long memberId, Long friendId) {
		if (friendId == null) {
			return TasteAnswers.NONE;
		}
		this.friends.findByIdAndOwnerMemberIdAndDeletedAtIsNull(friendId, memberId)
			.orElseThrow(() -> new CustomException(FriendErrorCode.NOT_FOUND));

		return this.tasteProfiles.findByFriendId(friendId)
			.map((profile) -> readAnswers(profile.getAnswers()))
			.orElse(TasteAnswers.NONE);
	}

	/** 없는 키와 깨진 JSON은 전부 NONE으로 흡수함 — 취향이 없다고 추천이 실패하면 안 됨. */
	private TasteAnswers readAnswers(String answersJson) {
		if (answersJson == null) {
			return TasteAnswers.NONE;
		}
		try {
			JsonNode node = this.objectMapper.readTree(answersJson);
			return new TasteAnswers(valuesOf(node, "colors", "color"), valuesOf(node, "styles", "style"));
		}
		catch (Exception ex) {
			return TasteAnswers.NONE;
		}
	}

	/** 복수 키와 단수 키를 **둘 다** 읽음. 복수는 설문 제출이, 단수는 시드가 씀 — 단수를 빼면 시드 취향이 사라짐. */
	private List<String> valuesOf(JsonNode node, String pluralKey, String singularKey) {
		List<String> values = new ArrayList<>();

		JsonNode plural = node.get(pluralKey);
		if (plural != null && plural.isArray()) {
			plural.forEach((element) -> addIfPresent(values, element));
		}
		addIfPresent(values, node.get(singularKey));

		return values;
	}

	private void addIfPresent(List<String> values, JsonNode node) {
		if (node == null || node.isNull()) {
			return;
		}
		String text = node.asString();
		if (!text.isBlank() && !values.contains(text)) {
			values.add(text);
		}
	}

	/**
	 * 취향 일치 가산임. 색상과 스타일 각각 2점으로 관계 태그와 같은 무게를 줌 — 기획 주제가 취향 미스매칭 보완이라 취향이 관계에 밀리면 안 됨.
	 */
	private int tasteScore(Product product, List<String> tags, TasteAnswers taste) {
		int score = 0;
		// 다중 선택은 축당 한 번만 가산함. 맞은 개수만큼 주면 많이 고른 사람이 관계 근거를 밀어냄
		if (taste.colors().contains(product.getColor())) {
			score += 2;
		}
		if (taste.styles().stream().anyMatch(tags::contains)) {
			score += 2;
		}
		return score;
	}

	/** 근거 문구에 쓸 취향임. 색과 스타일이 둘 다 맞아도 문구에는 하나만 싣고 색을 먼저 씀. 일치가 없으면 null이라 관계 근거로 떨어짐. */
	private String tasteHitOf(Product product, List<String> tags, TasteAnswers taste) {
		if (taste.colors().contains(product.getColor())) {
			return product.getColor() + " 계열";
		}
		// **실제로 맞은** 스타일을 실음. 고른 것 중 첫 값을 쓰면 맞지도 않은 스타일이 근거로 찍힘
		return taste.styles().stream().filter(tags::contains).findFirst().map((style) -> style + " 스타일").orElse(null);
	}

	/** 입력 예산 단위는 만원임(화면 표기 그대로). 원 단위 환산은 컨트롤러가 아니라 여기서 함. */
	private Calculated calculate(String relation, int minBudgetInTenThousand, int maxBudgetInTenThousand,
			TasteAnswers taste, boolean aiReason) {
		// 모르는 관계는 선호 태그가 없어 조용히 일반 추천이 나감 — 화면이 보내는 4종만 받음
		if (!RELATION_TAG.containsKey(relation)) {
			throw new CustomException(RecommendErrorCode.INVALID_CONDITION);
		}
		// ADR-008: 예산은 0 이상. 음수는 예산에 맞는 상품이 0건이 되어 전체 카탈로그 폴백으로 흡수되므로 여기서 막아야 함
		if (minBudgetInTenThousand < 0 || maxBudgetInTenThousand < 0) {
			throw new CustomException(RecommendErrorCode.INVALID_CONDITION);
		}

		int minBudget = minBudgetInTenThousand * 10000;
		int maxBudget = maxBudgetInTenThousand * 10000;

		// ADR-008: 최소가 최대를 넘을 수 없음
		if (minBudget > maxBudget) {
			throw new CustomException(RecommendErrorCode.BUDGET_INVERTED);
		}

		// 선물 적격만 남김. 시드의 코디용 의류는 FR-023 스타일링 제안 대상이지 선물 대상이 아님 —
		// 안 거르면 "친구에게 슬랙스를 선물하세요"가 나감. **두 갈래를 다 걸러야 함**: 예산 필터와 아래 전체 폴백
		List<Product> inBudget = this.products.findByPriceBetweenOrderById(minBudget, maxBudget)
			.stream()
			.filter(Product::isGiftEligible)
			.toList();
		// 예산에 맞는 것이 없으면 빈 화면을 주지 않고 전체에서 고름 — 시연 중 결과 0건을 피함
		List<Product> pool = inBudget.isEmpty()
				? this.products.findAllByOrderById().stream().filter(Product::isGiftEligible).toList() : inBudget;

		String preferredTag = RELATION_TAG.get(relation);

		List<Scored> scored = new ArrayList<>(pool.stream().map((product) -> {
			List<String> tags = readTags(product.getStyleTags());
			int score = tags.contains(preferredTag) ? 2 : (tags.contains("미니멀") ? 1 : 0);
			return new Scored(product, tags, score + tasteScore(product, tags, taste),
					tasteHitOf(product, tags, taste));
		}).toList());

		// 안정 정렬이라 점수가 같으면 id 순서가 유지됨(스모크가 첫 항목의 근거 유형을 봄)
		scored.sort(Comparator.comparingInt(Scored::score).reversed());

		List<Scored> ruleTop = scored.subList(0, Math.min(RESULT_SIZE, scored.size()));
		if (!aiReason) {
			// 옵트인이 아니면 선정기를 부르지도 않음 — 비용과 지연과 실패 면적이 전부 사라짐
			return new Calculated(assemble(ruleTop, relation, preferredTag, null), "RULE");
		}

		ModelPick modelPick = pickByModel(scored, relation, taste);
		if (modelPick == null) {
			return new Calculated(assemble(ruleTop, relation, preferredTag, null), "RULE");
		}
		return new Calculated(assemble(modelPick.items(), relation, preferredTag, modelPick.reason()), "LLM");
	}

	/**
	 * 모델이 고른 것임. **서비스가 싱글턴이라 필드로 들고 다니면 동시 요청끼리 근거가 섞임** — 그래서 반환값으로 묶어서 넘김.
	 */
	private record ModelPick(List<Scored> items, String reason) {
	}

	/**
	 * 모델에게 후보를 주고 고르게 함. 못 고르거나 **후보 밖 상품을 고르면 `null`을 돌려줌** — 호출부가 규칙 결과로 떨어짐.
	 *
	 * 후보 대조를 여기서 한 번 더 하는 이유는 `GiftPicker`가 인터페이스라 어떤 구현이든 붙을 수 있기 때문임. Bedrock 구현은 이미
	 * `GiftPickNormalizer`로 걸렀지만 그것에 의존하지 않음.
	 */
	private ModelPick pickByModel(List<Scored> scored, String relation, TasteAnswers taste) {
		List<Scored> candidates = scored.subList(0, Math.min(CANDIDATE_SIZE, scored.size()));
		GiftPicker.Picked picked = this.giftPicker.pick(candidates.stream().map(Scored::product).toList(), relation,
				taste.colors(), taste.styles());
		if (picked == null) {
			return null;
		}

		Map<Long, Scored> byId = new LinkedHashMap<>();
		candidates.forEach((item) -> byId.put(item.product().getId(), item));

		List<Scored> chosen = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (Long productId : picked.productIds()) {
			Scored item = byId.get(productId);
			// 같은 id를 두 번 주면 같은 Scored가 두 번 담겨 개수 검사를 통과함. 그러면 화면에 같은 카드가 둘 뜸
			if (item == null || !seen.add(productId)) {
				return null;
			}
			chosen.add(item);
		}
		if (chosen.size() != Math.min(RESULT_SIZE, candidates.size())) {
			return null;
		}
		// 근거가 없으면 규칙 문구가 나가야 하는데 그건 폴백과 구분이 안 됨. 아예 통째로 폴백함
		if (picked.reason() == null || picked.reason().isBlank()) {
			return null;
		}

		return new ModelPick(chosen, picked.reason());
	}

	/**
	 * 결과 조립임. **재배열이 아니라 재조립이어야 함** — 근거가 순위와 함께 정해지므로, 기존 결과를 순서만 바꾸면 과거 2위의 고정 문구가 새
	 * 1위에 남음.
	 *
	 * `firstReason`이 있으면 모델이 쓴 것이고 그때 1위는 **점수와 무관하게 PERSONAL임**(모델이 개인화 문구를 실제로 썼기 때문임).
	 * 없으면 기존 규칙 그대로 점수가 0보다 클 때만 PERSONAL임.
	 */
	private List<Result> assemble(List<Scored> items, String relation, String preferredTag, String firstReason) {
		List<Result> results = new ArrayList<>();
		for (int rank = 0; rank < items.size(); rank++) {
			Scored item = items.get(rank);
			boolean personal = (rank == 0) && (firstReason != null || item.score() > 0);

			results.add(new Result(
					new ProductView(item.product().getId(), item.product().getName(),
							(item.product().getPrice() == null) ? 0 : item.product().getPrice(),
							item.product().getColor(), item.product().getImageUrl()),
					personal ? "PERSONAL" : "GENERAL",
					personal ? firstReasonOf(item, relation, preferredTag, firstReason) : GENERAL_REASON));
		}
		return results;
	}

	private String firstReasonOf(Scored item, String relation, String preferredTag, String firstReason) {
		return (firstReason != null) ? firstReason : personalReason(item, relation, preferredTag);
	}

	/**
	 * 근거 문구에 쓸 태그임. **실제로 점수를 준 태그**를 돌려줘야 함 — 예전에는 상품의 첫 태그를 그대로 썼는데, 그러면 시드 태그 순서가 바뀔 때
	 * "연인에게 어울리는 캐주얼 라인이에요"처럼 관계와 무관한 문구가 나감. PERSONAL은 점수가 0보다 클 때만 붙으므로 둘 중 하나는 반드시 있음.
	 */
	private String matchedTag(Scored item, String preferredTag) {
		return item.tags().contains(preferredTag) ? preferredTag : "미니멀";
	}

	/**
	 * 취향이 맞았으면 그것을 근거로 씀 — 기획 주제가 취향 미스매칭 보완이라 화면에 "왜 이걸 골랐는지"가 취향으로 읽혀야 함. 취향이 없거나 안
	 * 맞았으면 기존 관계 근거로 떨어짐.
	 */
	private String personalReason(Scored item, String relation, String preferredTag) {
		if (item.tasteHit() != null) {
			return item.tasteHit() + "을 좋아하신다고 하셔서 골랐어요";
		}
		return relation + "에게 어울리는 " + matchedTag(item, preferredTag) + " 라인이에요";
	}

	private record Scored(Product product, List<String> tags, int score, String tasteHit) {
	}

	private ProductView productViewOf(Long productId) {
		// FK가 RESTRICT라 상품이 사라질 수 없음. 그래도 조회 실패를 예외로 올리지 않고 표시용 기본값을 주는 이유는
		// 재조회가 과거 기록 열람이고 여기서 500을 내면 편지함 화면 전체가 죽기 때문임
		return this.products.findById(productId)
			.map((product) -> new ProductView(product.getId(), product.getName(),
					(product.getPrice() == null) ? 0 : product.getPrice(), product.getColor(), product.getImageUrl()))
			.orElseGet(() -> new ProductView(productId, "삭제된 상품", 0, null, null));
	}

	private String toContextJson(String relation, int minBudget, int maxBudget, Long friendId) {
		try {
			// 단위는 만원(요청 그대로). 목적과 상황은 현재 요청에 없어 지어내지 않음 — 요청이 늘면 키만 더함
			Map<String, Object> context = new LinkedHashMap<>();
			context.put("relation", relation);
			context.put("minBudget", minBudget);
			context.put("maxBudget", maxBudget);
			// 스냅샷 재조회가 근거를 재현하려면 어느 친구의 취향으로 뽑았는지가 남아야 함
			if (friendId != null) {
				context.put("friendId", friendId);
			}
			return this.objectMapper.writeValueAsString(context);
		}
		catch (Exception ex) {
			throw new IllegalStateException("추천 맥락 직렬화 실패", ex);
		}
	}

	private JsonNode readTree(String json) {
		try {
			return this.objectMapper.readTree(json);
		}
		catch (Exception ex) {
			return this.objectMapper.createObjectNode();
		}
	}

	private List<String> readTags(String styleTagsJson) {
		if (styleTagsJson == null) {
			return List.of();
		}
		try {
			JsonNode node = this.objectMapper.readTree(styleTagsJson);
			if (!node.isArray()) {
				return List.of();
			}
			List<String> tags = new ArrayList<>();
			node.forEach((element) -> tags.add(element.asString()));
			return tags;
		}
		catch (Exception ex) {
			return List.of();
		}
	}

}
