package com.mcmory.backend.recommend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시드가 v1.1 실측 선택지를 덮는지 지키는 테스트임(FIX-W001 T2).
 *
 * 이 클래스가 없으면 시드가 조용히 어긋나도 아무도 모름 — 2026-08-09까지 실제로 그랬고, 취향 색상 6종 중 넷과 스타일 6종 중 둘이 시드에
 * 없어서 그 값을 고른 사용자에게 개인화 추천이 나가지 않았음. 시연에서 티가 안 나는 종류의 결함이라 자동 방어선이 필요함.
 *
 * **색상으로 추천을 요청하는 검증은 넣을 수 없음** — POST /api/v1/recommend는 relation과 예산만 받음. 색상은 응답에 실려 오는
 * 상품 속성이라 커버리지를 그쪽에서 셈.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecommendSeedCoverageIntegrationTest extends HttpIntegrationSupport {

	/** v1.1 HOME-02 실측. 편지지 색상 4종과 다른 축이니 섞지 말 것. */
	private static final List<String> TASTE_COLORS = List.of("코냑", "블랙", "베이지", "핑크", "골드", "그레이");

	/** v1.1 HOME-02 실측. RELATION_TAG의 값도 이 안에서만 골라야 함. */
	private static final List<String> STYLES = List.of("캐주얼", "미니멀", "스트릿", "클래식", "러블리", "포멀");

	/** 화면 HOME-01의 관계 칩 4종과 그 선호 태그. RecommendService.RELATION_TAG와 같아야 함. */
	private static final List<String> RELATIONS = List.of("연인", "친구", "부모님", "스승/제자");

	private static final List<String> PREFERRED = List.of("러블리", "캐주얼", "클래식", "포멀");

	/** 화면 HOME-01 슬라이더 기본값(만원). 이 대역에서 네 관계가 모두 개인화되어야 함. */
	private static final String DEFAULT_BUDGET = "\"minBudget\":50,\"maxBudget\":150";

	@BeforeEach
	void 로그인() {
		clearCookies();
		loginAs("01012345678", "1234");
	}

	@Test
	void 관계_넷_모두_첫_결과가_개인화이고_근거가_그_관계의_태그를_말한다() {
		List<String> broken = new ArrayList<>();

		for (int i = 0; i < RELATIONS.size(); i++) {
			String relation = RELATIONS.get(i);
			String tag = PREFERRED.get(i);

			var response = post("/api/v1/recommend", "{\"relation\":\"" + relation + "\"," + DEFAULT_BUDGET + "}");
			assertThat(response.status()).as(response.text()).isEqualTo(200);

			var first = response.body().get("results").get(0);
			String reasonType = first.get("reasonType").asString();
			String reason = first.get("reason").asString();

			// 태그가 시드에 없으면 점수가 0이 되어 GENERAL로 떨어짐. 즉 이 단언 하나가
			// 재매핑과 시드 커버리지를 함께 지킴
			if (!"PERSONAL".equals(reasonType) || !reason.contains(tag)) {
				broken.add(relation + " -> " + reasonType + " / " + reason);
			}
		}

		assertThat(broken).as("관계별 개인화가 깨진 것").isEmpty();
	}

	@Test
	void 시드가_취향_색상_여섯과_스타일_여섯을_모두_덮는다() {
		// 예산을 넓게 잡아 전체 카탈로그를 받음. 관계는 아무거나 — 여기서 보는 것은 커버리지뿐임
		var response = post("/api/v1/recommend", "{\"relation\":\"친구\",\"minBudget\":0,\"maxBudget\":100000}");
		assertThat(response.status()).as(response.text()).isEqualTo(200);

		// 추천 응답은 3건만 주므로 색상 커버리지는 여기서 못 봄. 카탈로그 전체를 보는 경로가
		// 계약에 없어 상품 조회 대신 시드 파일을 직접 읽음 — 시드가 정본이고 이 테스트가 지키려는 것도 시드임
		String seed = readSeed();

		Set<String> missingColors = new HashSet<>();
		for (String color : TASTE_COLORS) {
			if (!seed.contains("'" + color + "'")) {
				missingColors.add(color);
			}
		}
		assertThat(missingColors).as("시드에 없는 취향 색상").isEmpty();

		Set<String> missingStyles = new HashSet<>();
		for (String style : STYLES) {
			if (!seed.contains("'" + style + "'")) {
				missingStyles.add(style);
			}
		}
		assertThat(missingStyles).as("시드에 없는 스타일").isEmpty();
	}

	@Test
	void 시드_스타일_태그에_관계_태그가_섞여_있지_않다() {
		// 데일리와 실용과 합리적은 옛 RELATION_TAG 값임. 옷 스타일이 아니라 매핑을 위해 심어둔 것이었고
		// 그래서 두 축이 한 컬럼에서 뒤엉켰음. 되살아나면 여기서 빨개짐
		String seed = readSeed();
		List<String> leaked = new ArrayList<>();
		for (String stale : List.of("데일리", "실용", "합리적")) {
			if (seed.contains("'" + stale + "'")) {
				leaked.add(stale);
			}
		}
		assertThat(leaked).as("style_tags에 되살아난 관계 태그").isEmpty();
	}

	private String readSeed() {
		try (var in = getClass().getResourceAsStream("/db/02-seed.sql")) {
			assertThat(in).as("시드 리소스").isNotNull();
			return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException("시드를 읽지 못함", ex);
		}
	}

}
