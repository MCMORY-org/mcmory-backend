package com.mcmory.backend.common;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도메인을 가로지르는 입력 경계임 — 추천 조건, 동의 필드, 선물 수신자 이름, 전화번호 정규화.
 *
 * 대부분은 "200이 나오면 안 되는데 나온다"를 막는 것이지만, 하이픈 전화번호와 취향 재저장 둘은 반대로 **조임이 과해져 200이 안 나오게 되는
 * 것**을 막는 테스트임.
 *
 * 예약 쪽 경계는 ReservationGuardIntegrationTest에 있음.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InputGuardIntegrationTest extends HttpIntegrationSupport {

	private static final String PHONE = "01012345678";

	private static final String PASSWORD = "1234";

	@BeforeEach
	void 로그인() {
		clearCookies();
		loginAs(PHONE, PASSWORD);
	}

	@Test
	void 모르는_관계로는_추천을_받을_수_없다() {
		// 화이트리스트가 없으면 선호 태그만 비어 조용히 일반 추천이 나갔음
		Response response = post("/api/v1/recommend", "{\"relation\":\"UNKNOWN\",\"minBudget\":10,\"maxBudget\":100}");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("REC400_4");
	}

	@Test
	void 음수_예산으로는_추천을_받을_수_없다() {
		// 음수 범위는 결과 0건이 되어 전체 카탈로그 폴백에 흡수됐음 — 오히려 200이 나왔다
		Response response = post("/api/v1/recommend", "{\"relation\":\"친구\",\"minBudget\":-10,\"maxBudget\":-1}");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("REC400_4");
	}

	@Test
	void 동의_요청에_agreed가_빠지면_철회로_처리되지_않는다() {
		// 누락을 false로 접으면 필드를 빠뜨린 요청이 조용히 철회가 됨. 동의와 철회는 감사 대상이라 명시를 요구함
		Response response = post("/api/v1/consents", "{\"type\":\"SMS\"}");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("VALID400_2");
	}

	@Test
	void 수신자_이름이_공백이면_기본값으로_저장된다() {
		Response response = 선물을_보낸다("   ");

		assertThat(response.status()).isEqualTo(200);

		// "친구"가 있는지만 보면 이미 있던 행 때문에 공백 친구가 생겨도 통과함 — 빈 이름 부재를 함께 단언함
		Response friends = get("/api/v1/friends");
		boolean hasDefaultNamedFriend = false;
		for (var friend : friends.body().get("list")) {
			String name = friend.get("name").isNull() ? null : friend.get("name").asString();
			assertThat(name == null || !name.isBlank()).as(friends.text()).isTrue();
			hasDefaultNamedFriend = hasDefaultNamedFriend || "친구".equals(name);
		}
		assertThat(hasDefaultNamedFriend).as(friends.text()).isTrue();
	}

	@Test
	void 수신자_이름이_컬럼_길이를_넘으면_500이_아니라_400이다() {
		// 발송 경로는 FriendService.create()를 거치지 않아 길이 검증을 못 받았고 DB 길이 오류로 500이 났음
		Response response = 선물을_보낸다("가".repeat(21));

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("FRIEND400_1");
	}

	@Test
	void 문자가_섞인_전화번호로는_로그인할_수_없다() {
		// 모든 비숫자를 걷어내면 abc01012345678xyz가 정상 번호로 통과했음
		clearCookies();
		Response response = post("/api/v1/auth/login",
				"{\"phone\":\"abc" + PHONE + "xyz\",\"password\":\"" + PASSWORD + "\"}");

		// 상태만 보면 COMMON500도 통과함 — 오류 분류까지 고정해야 500 누출을 잡음
		assertThat(response.status()).isEqualTo(401);
		assertThat(response.code()).isEqualTo("AUTH401_2");
	}

	@Test
	void 하이픈이_섞인_전화번호로는_로그인할_수_있다() {
		// 위 조임이 하이픈 입력까지 막으면 안 됨(ADR-008의 1번)
		clearCookies();
		Response response = post("/api/v1/auth/login",
				"{\"phone\":\"010-1234-5678\",\"password\":\"" + PASSWORD + "\"}");

		assertThat(response.status()).isEqualTo(200);
	}

	@Test
	void 취향을_다시_저장해도_덮어쓰기가_된다() {
		// taste_profile에 UNIQUE를 걸면서 delete 후 insert의 flush 순서 때문에 깨질 수 있는 지점임
		long friendId = get("/api/v1/friends").body().get("list").get(0).get("id").asLong();

		for (String summary : new String[] { "첫 저장", "두 번째", "세 번째" }) {
			Response response = patch("/api/v1/friends",
					"{\"id\":" + friendId + ",\"tasteSummary\":\"" + summary + "\"}");
			assertThat(response.status()).as(summary).isEqualTo(200);
		}
	}

	@Test
	void 같은_번호면_친구_이름을_고칠_수_있다() {
		long id = 검증용_친구_id("01077770001");
		String hyphenated = "010-7777-0001";

		Response response = patch("/api/v1/friends/" + id, "{\"name\":\"고친 이름\",\"phone\":\"" + hyphenated + "\"}");

		assertThat(response.status()).as(response.text()).isEqualTo(200);
		// 응답만 보면 부분 갱신 버그를 놓침 — 재조회로 확인함
		assertThat(이름을_읽는다(id)).isEqualTo("고친 이름");
	}

	@Test
	void 번호가_다르면_수정하지_않고_거절한다() {
		long id = 검증용_친구_id("01077770002");
		String before = 이름을_읽는다(id);

		Response response = patch("/api/v1/friends/" + id, "{\"name\":\"바뀌면 안 됨\",\"phone\":\"01000009999\"}");

		assertThat(response.status()).isEqualTo(409);
		assertThat(response.code()).isEqualTo("FRIEND409_2");
		assertThat(이름을_읽는다(id)).isEqualTo(before);
	}

	@Test
	void 친구_수정에_번호가_빠지면_500이_아니라_400이다() {
		long id = 검증용_친구_id("01077770003");

		Response response = patch("/api/v1/friends/" + id, "{\"name\":\"이름만\"}");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("FRIEND400_2");
	}

	@Test
	void 보유_제품의_관리_방법을_카테고리로_받는다() {
		post("/api/v1/owned", "{\"serial\":\"MX2024A031\"}");
		long ownedId = get("/api/v1/owned").body().get("list").get(0).get("id").asLong();

		Response response = get("/api/v1/owned/" + ownedId + "/care-guide");

		assertThat(response.status()).as(response.text()).isEqualTo(200);
		assertThat(response.body().get("category").asString()).isNotBlank();
		assertThat(response.body().get("items").size()).isGreaterThan(0);
	}

	@Test
	void 없는_보유_제품의_관리_방법은_404다() {
		Response response = get("/api/v1/owned/999999/care-guide");

		assertThat(response.status()).isEqualTo(404);
		assertThat(response.code()).isEqualTo("OWNED404_2");
	}

	/**
	 * 수정 테스트 전용 친구를 만들어 씀. **시드 친구를 개명하면 안 됨** — 같은 컨테이너를 쓰는
	 * GiftLifecycleIntegrationTest가 시드 "친구 2"를 이름으로 찾으므로 개명하는 순간 그쪽이 수신자 해석에 실패함(실제로 한 번
	 * 깨뜨렸음).
	 */
	private long 검증용_친구_id(String phone) {
		post("/api/v1/friends", "{\"name\":\"검증용 친구\",\"phone\":\"" + phone + "\"}");

		for (var friend : get("/api/v1/friends").body().get("list")) {
			if (!friend.get("phone").isNull() && phone.equals(friend.get("phone").asString())) {
				return friend.get("id").asLong();
			}
		}
		throw new AssertionError("검증용 친구를 만들지 못함: " + phone);
	}

	/**
	 * v1.1이 편지 본문을 500자에서 200자로 줄였는데 경계를 지키는 테스트가 없었음 — 값을 되돌려도 아무것도 깨지지 않는 상태였음.
	 *
	 * 초과만 보면 한도를 늘렸을 때 통과하고, 허용만 보면 한도를 줄였을 때 통과함. 양쪽을 함께 잡아야 값이 고정됨.
	 */
	@Test
	void 편지_본문은_200자까지이고_201자는_400이다() {
		Response recommended = post("/api/v1/recommend", "{\"relation\":\"친구\",\"minBudget\":0,\"maxBudget\":500}");
		long productId = recommended.body().get("results").get(0).get("product").get("id").asLong();

		Response exact = post("/api/v1/gift", "{\"productId\":" + productId + ",\"letterBody\":\"" + "가".repeat(200)
				+ "\",\"friendName\":\"경계 확인\"}");
		assertThat(exact.status()).as(exact.text()).isEqualTo(200);

		Response over = post("/api/v1/gift", "{\"productId\":" + productId + ",\"letterBody\":\"" + "가".repeat(201)
				+ "\",\"friendName\":\"경계 초과\"}");
		assertThat(over.status()).as(over.text()).isEqualTo(400);
		assertThat(over.code()).isEqualTo("GIFT400_2");
	}

	private String 이름을_읽는다(long friendId) {
		for (var friend : get("/api/v1/friends").body().get("list")) {
			if (friend.get("id").asLong() == friendId) {
				return friend.get("name").isNull() ? null : friend.get("name").asString();
			}
		}
		return null;
	}

	private Response 선물을_보낸다(String friendName) {
		Response recommended = post("/api/v1/recommend", "{\"relation\":\"친구\",\"minBudget\":0,\"maxBudget\":500}");
		assertThat(recommended.status()).isEqualTo(200);
		long productId = recommended.body().get("results").get(0).get("product").get("id").asLong();

		return post("/api/v1/gift",
				"{\"productId\":" + productId + ",\"letterBody\":\"검증용 편지\",\"friendName\":\"" + friendName + "\"}");
	}

}
