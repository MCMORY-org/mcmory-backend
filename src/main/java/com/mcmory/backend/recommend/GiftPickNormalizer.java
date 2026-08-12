package com.mcmory.backend.recommend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mcmory.backend.styling.StylingReasonNormalizer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 모델이 고른 상품 목록을 쓸 수 있는 형태로 만드는 것임. **순수 함수라 단위 테스트 대상임** — 클라이언트를 통째로 모킹하면 이 로직이 한 줄도 안
 * 돌아감(스타일링 정규화와 같은 이유임).
 *
 * **이것이 환각 차단 지점임.** 후보 밖 id가 하나라도 있으면 통째로 버림 — 일부만 살리면 순위가 뒤틀리고, 그 상품이 그대로 화면에 나가면 계약
 * 오염임(`StylingService`가 애초에 상품 선택을 모델에 안 맡긴 이유와 같음).
 *
 * 근거 문구 검사는 `StylingReasonNormalizer`를 그대로 씀 — 36자 상한과 금칙어와 영문 차단이 이미 실측으로 다듬어져 있어 새로 쓰면
 * 퇴보임.
 */
public final class GiftPickNormalizer {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** 거부 사유임. 모든 거부가 조용하므로 **이 값이 유일한 관측 지점임** — 안 남기면 폴백 비율을 분해할 수 없음. */
	public enum Rejection {

		BAD_JSON, WRONG_SIZE, DUPLICATE, UNKNOWN_ID, BAD_REASON

	}

	/** 통과면 `productIds`가 있고 `rejection`이 null임. */
	public record Result(List<Long> productIds, String reason, Rejection rejection) {

		public boolean accepted() {
			return this.productIds != null;
		}

		static Result of(List<Long> productIds, String reason) {
			return new Result(productIds, reason, null);
		}

		static Result rejected(Rejection rejection) {
			return new Result(null, null, rejection);
		}

	}

	private GiftPickNormalizer() {
	}

	/**
	 * @param raw 모델 원문임
	 * @param candidateIds 규칙이 만든 후보 집합임. **이 밖의 id는 전부 환각임**
	 * @param expectedSize 기대 개수임. 후보가 3건이 안 되면 그만큼만 요구함
	 */
	public static Result normalize(String raw, Set<Long> candidateIds, int expectedSize) {
		if (raw == null) {
			return Result.rejected(Rejection.BAD_JSON);
		}

		JsonNode node;
		try {
			node = MAPPER.readTree(unwrap(raw));
		}
		catch (RuntimeException ex) {
			return Result.rejected(Rejection.BAD_JSON);
		}

		JsonNode picks = node.get("picks");
		if (picks == null || !picks.isArray()) {
			return Result.rejected(Rejection.BAD_JSON);
		}

		List<Long> productIds = new ArrayList<>();
		for (JsonNode element : picks) {
			if (!element.isNumber()) {
				return Result.rejected(Rejection.BAD_JSON);
			}
			productIds.add(element.asLong());
		}

		if (productIds.size() != expectedSize) {
			return Result.rejected(Rejection.WRONG_SIZE);
		}
		// 개수만 세면 같은 상품이 둘 나오는 것을 못 잡음. 그러면 화면에 같은 카드가 둘 뜸
		if (new HashSet<>(productIds).size() != productIds.size()) {
			return Result.rejected(Rejection.DUPLICATE);
		}
		if (!candidateIds.containsAll(productIds)) {
			return Result.rejected(Rejection.UNKNOWN_ID);
		}

		JsonNode reason = node.get("reason");
		StylingReasonNormalizer.Result normalized = StylingReasonNormalizer
			.normalize((reason == null || reason.isNull()) ? null : reason.asString());
		if (!normalized.accepted()) {
			return Result.rejected(Rejection.BAD_REASON);
		}

		return Result.of(List.copyOf(productIds), normalized.text());
	}

	/**
	 * 모델이 JSON을 코드블록이나 설명 문장으로 감싸는 일이 잦음. 그것 때문에 전부 버리면 폴백률만 올라감. 첫 여는 중괄호부터 마지막 닫는
	 * 중괄호까지만 봄 — 중괄호가 없으면 그대로 넘겨 파싱이 실패하게 둠(그게 진짜 깨진 JSON임).
	 */
	private static String unwrap(String raw) {
		String trimmed = raw.trim();
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		if (start < 0 || end < start) {
			return trimmed;
		}
		return trimmed.substring(start, end + 1);
	}

}
