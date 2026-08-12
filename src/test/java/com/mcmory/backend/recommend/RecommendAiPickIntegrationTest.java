package com.mcmory.backend.recommend;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.mcmory.backend.product.Product;
import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `aiReason` 옵트인이 실제로 선정기를 부르고, 그 결과로 **결과를 다시 조립**하는지 보는 것임.
 *
 * **별도 클래스인 이유**: 빈을 갈아 끼우면 Spring 컨텍스트가 새로 떠서 콜드 스타트가 약 2분 붙음. 그래서 옵트인·폴백·미호출 셋을 이 클래스
 * 하나에 몰아 그 값을 한 번만 냄(`StylingAiReasonIntegrationTest`와 같은 판단임).
 *
 * **Bedrock을 부르지 않음** — 스텁이 고정 답을 돌려줌. 통합 테스트가 외부를 부르면 결과가 비결정적이 됨.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecommendAiPickIntegrationTest extends HttpIntegrationSupport {

	private static final String STUB_REASON = "스텁이 고른 이유예요";

	private static final String GENERAL_REASON = "모델이 함께 매치한 제품이에요";

	/** 규칙 순서와 **반드시 다른** 순서를 만듦. 같으면 재조립이 됐는지 안 됐는지 구분이 안 됨. */
	static class StubPicker implements GiftPicker {

		private final AtomicInteger calls = new AtomicInteger();

		private volatile boolean returnNull;

		/** 규칙 상위 3건을 **뒤집어서** 고름. 재배열 결함을 정확히 찌르는 모드임. */
		private volatile boolean reverseRuleTop;

		/** 같은 상품을 두 번 고름. 후보 안의 id라 개수만 세는 검사로는 안 잡힘. */
		private volatile boolean duplicate;

		private volatile List<Long> lastCandidateIds = List.of();

		@Override
		public Picked pick(List<Product> candidates, String relation, List<String> colors, List<String> styles) {
			this.calls.incrementAndGet();
			this.lastCandidateIds = candidates.stream().map(Product::getId).toList();
			if (this.returnNull) {
				return null;
			}
			if (this.duplicate) {
				Long first = this.lastCandidateIds.get(0);
				return new Picked(List.of(first, first, this.lastCandidateIds.get(1)), STUB_REASON);
			}
			if (this.reverseRuleTop) {
				List<Long> top = new ArrayList<>(this.lastCandidateIds.subList(0, 3));
				java.util.Collections.reverse(top);
				return new Picked(top, STUB_REASON);
			}
			// 뒤에서부터 셋을 골라 규칙 상위 3건과 겹치지 않게 함
			List<Long> picked = new ArrayList<>(
					this.lastCandidateIds.subList(this.lastCandidateIds.size() - 3, this.lastCandidateIds.size()));
			return new Picked(picked, STUB_REASON);
		}

	}

	@TestConfiguration
	static class StubPickerConfig {

		@Bean
		@Primary
		StubPicker stubPicker() {
			return new StubPicker();
		}

	}

	@Autowired
	private StubPicker picker;

	@BeforeEach
	void 로그인하고_스텁을_초기화한다() {
		clearCookies();
		loginAs("01012345678", "1234");
		this.picker.calls.set(0);
		this.picker.returnNull = false;
		this.picker.reverseRuleTop = false;
		this.picker.duplicate = false;
	}

	private Response recommend(String query) {
		return post("/api/v1/recommend" + query, "{\"relation\":\"친구\",\"minBudget\":0,\"maxBudget\":500}");
	}

	/**
	 * 옵트인하면 **선정기가 고른 순서 그대로** 결과가 나가고, 1위에만 스텁 문구가 붙음.
	 *
	 * 2·3위가 고정 문구인지 함께 봄 — 기존 Result를 재배열만 하면 과거 2위의 고정 문구가 새 1위에 남으므로, 이 단언이 재조립의 증거임.
	 */
	@Test
	void 옵트인하면_선정기_순서로_다시_조립되고_reasonSource가_LLM이다() {
		Response response = recommend("?aiReason=true");

		assertThat(response.status()).as(response.text()).isEqualTo(200);
		assertThat(response.body().get("reasonSource").asString()).isEqualTo("LLM");

		List<Long> returned = new ArrayList<>();
		response.body().get("results").forEach((node) -> returned.add(node.get("product").get("id").asLong()));
		List<Long> expected = this.picker.lastCandidateIds.subList(this.picker.lastCandidateIds.size() - 3,
				this.picker.lastCandidateIds.size());
		assertThat(returned).containsExactlyElementsOf(expected);

		assertThat(response.body().get("results").get(0).get("reasonType").asString()).isEqualTo("PERSONAL");
		assertThat(response.body().get("results").get(0).get("reason").asString()).isEqualTo(STUB_REASON);
		assertThat(response.body().get("results").get(1).get("reasonType").asString()).isEqualTo("GENERAL");
		assertThat(response.body().get("results").get(1).get("reason").asString()).isEqualTo(GENERAL_REASON);
	}

	/**
	 * **재배열 결함을 정확히 찌르는 것임.** 모델이 규칙 상위 3건을 뒤집어 고르면, 순서만 바꾸는 구현은 과거 3위가 달고 있던 고정 문구를 새
	 * 1위에 그대로 남긴다. 재조립이면 새 1위가 PERSONAL과 모델 문구를 받고 나머지가 고정 문구를 받는다.
	 *
	 * 저장 경로가 이 값을 그대로 동결하므로 오염되면 DB까지 간다.
	 */
	@Test
	void 규칙_상위를_뒤집어_골라도_근거가_순위를_따라간다() {
		this.picker.reverseRuleTop = true;

		Response response = recommend("?aiReason=true");

		assertThat(response.status()).as(response.text()).isEqualTo(200);
		List<Long> returned = new ArrayList<>();
		response.body().get("results").forEach((node) -> returned.add(node.get("product").get("id").asLong()));
		List<Long> ruleTop = new ArrayList<>(this.picker.lastCandidateIds.subList(0, 3));
		java.util.Collections.reverse(ruleTop);
		assertThat(returned).containsExactlyElementsOf(ruleTop);

		assertThat(response.body().get("results").get(0).get("reason").asString()).isEqualTo(STUB_REASON);
		assertThat(response.body().get("results").get(1).get("reason").asString()).isEqualTo(GENERAL_REASON);
		assertThat(response.body().get("results").get(2).get("reason").asString()).isEqualTo(GENERAL_REASON);
	}

	/**
	 * **같은 상품을 두 번 고르면 통째로 버림.** 후보 안의 id라 개수 검사만으로는 통과해 같은 카드가 화면에 둘 뜨고 저장 행에도 둘 남음.
	 */
	@Test
	void 같은_상품을_두_번_고르면_규칙_결과로_폴백한다() {
		this.picker.duplicate = true;

		Response response = recommend("?aiReason=true");

		assertThat(response.status()).as(response.text()).isEqualTo(200);
		assertThat(response.body().get("reasonSource").asString()).isEqualTo("RULE");

		List<Long> returned = new ArrayList<>();
		response.body().get("results").forEach((node) -> returned.add(node.get("product").get("id").asLong()));
		assertThat(returned).doesNotHaveDuplicates();
	}

	/** 선정기가 못 고르면(null) 규칙 결과가 그대로 나감. **화면이 죽지 않는 것이 요구임.** */
	@Test
	void 선정기가_못_고르면_규칙_결과로_폴백한다() {
		this.picker.returnNull = true;

		Response response = recommend("?aiReason=true");

		assertThat(response.status()).as(response.text()).isEqualTo(200);
		assertThat(response.body().get("reasonSource").asString()).isEqualTo("RULE");
		assertThat(response.body().get("results")).hasSize(3);
		assertThat(response.body().get("results").get(0).get("reason").asString()).isNotEqualTo(STUB_REASON);
	}

	/**
	 * **기본 호출은 선정기를 아예 안 부름.** 스텁이 붙어 있는데도 호출 수가 0인 것이 그 증거임 — 이 단언이 깨지면 옵트인이 무력해져 비용과
	 * 지연이 되살아남.
	 */
	@Test
	void 기본_호출은_선정기를_부르지_않는다() {
		Response response = recommend("");

		assertThat(response.status()).as(response.text()).isEqualTo(200);
		assertThat(this.picker.calls.get()).isZero();
		assertThat(response.body().get("reasonSource").asString()).isEqualTo("RULE");
	}

}
