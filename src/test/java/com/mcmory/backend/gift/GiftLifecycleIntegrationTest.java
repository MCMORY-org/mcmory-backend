package com.mcmory.backend.gift;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수신함과 추천 이력임. 둘을 한 클래스에 두는 이유는 선물 한 건의 수명 주기(추천에서 발송, 도착, 열람까지)가 하나의 흐름이고 TEST-W001 4장이
 * 이 클래스에 그 범위를 배정했기 때문임.
 *
 * 시드 전제: 회원 1(테스터, 01012345678)이 발송자, 회원 2(수신자, 01099998888)가 수신자임. friend 101(친구 2)의
 * 전화번호가 회원 2와 같아 회원 수신자 경로가 성립함.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiftLifecycleIntegrationTest extends HttpIntegrationSupport {

	private static final String SENDER_PHONE = "01012345678";

	private static final String RECIPIENT_PHONE = "01099998888";

	private static final String PASSWORD = "1234";

	@BeforeEach
	void 발송자로_로그인() {
		clearCookies();
		loginAs(SENDER_PHONE, PASSWORD);
	}

	@Test
	void 전화번호가_회원과_일치하는_친구에게_보내면_수신함에_잡히고_도착_알림이_생긴다() {
		String nickname = 선물을_보낸다("친구 2").body().get("nickname").asString();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		var letters = get("/api/v1/letters");
		assertThat(letters.status()).isEqualTo(200);
		assertThat(nicknamesOf(letters)).contains(nickname);

		var notifications = get("/api/v1/notifications");
		assertThat(notifications.body().get("unread").asInt()).isPositive();
		assertThat(notifications.text()).contains("GIFT_ARRIVED");
	}

	@Test
	void 수신함_항목에는_발신자_식별_정보가_없다() {
		선물을_보낸다("친구 2");

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		var received = get("/api/v1/letters").body().get("received");
		assertThat(received).isNotEmpty();

		var first = received.get(0);
		assertThat(first.has("senderMemberId")).isFalse();
		assertThat(first.has("friendName")).isFalse();
		// ADR-001: 발신자 쪽에서 나가는 값은 익명 닉네임 하나뿐임
		assertThat(first.get("nickname").asString()).contains("호저");
	}

	@Test
	void 전화번호_없는_친구에게_보낸_선물은_수신함에_잡히지_않는다() {
		String nickname = 선물을_보낸다("번호없는친구").body().get("nickname").asString();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		assertThat(nicknamesOf(get("/api/v1/letters"))).doesNotContain(nickname);
	}

	@Test
	void 회원_수신자도_동의_전에는_본문을_받지_못한다() {
		String token = 선물을_보낸다("친구 2").body().get("token").asString();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		// 수신함은 목록일 뿐이고 본문은 동의 게이트를 지나는 토큰 경로에서만 나옴(FR-015).
		// 회원이라는 사실이 동의 면제 사유가 아님
		var beforeConsent = get("/api/v1/g/" + token);
		assertThat(beforeConsent.body().get("needConsent").asBoolean()).isTrue();
		assertThat(beforeConsent.body().has("letterBody")).isFalse();

		assertThat(post("/api/v1/g/" + token, null).status()).isEqualTo(200);

		var afterConsent = get("/api/v1/g/" + token);
		assertThat(afterConsent.body().get("letterBody").asString()).isEqualTo("생일 축하해");
		assertThat(afterConsent.body().get("openedAt").isNull()).isFalse();
	}

	@Test
	void 추천_저장_후_같은_ID로_재조회하면_순위와_근거가_그대로다() {
		var created = 추천을_만든다();
		long recommendationId = created.body().get("recommendationId").asLong();

		var snapshot = get("/api/v1/recommend/" + recommendationId);
		assertThat(snapshot.status()).isEqualTo(200);
		assertThat(snapshot.body().get("results")).hasSize(3);
		assertThat(snapshot.body().get("context").get("relation").asString()).isEqualTo("연인");

		var first = snapshot.body().get("results").get(0);
		assertThat(first.get("rankNo").asInt()).isEqualTo(1);
		assertThat(first.get("reasonType").asString())
			.isEqualTo(created.body().get("results").get(0).get("reasonType").asString());
	}

	@Test
	void 같은_추천을_같은_친구에게_두_번_귀속해도_둘_다_200이다() {
		long recommendationId = 추천을_만든다().body().get("recommendationId").asLong();
		long friendId = 친구_101의_id();

		String body = "{\"friendId\":" + friendId + "}";
		assertThat(post("/api/v1/recommend/" + recommendationId + "/save", body).status()).isEqualTo(200);
		assertThat(post("/api/v1/recommend/" + recommendationId + "/save", body).status()).isEqualTo(200);

		// 귀속은 컬럼 UPDATE라 재호출로 행이 늘 수 없음. 그것이 여기서 말하는 멱등임
		assertThat(get("/api/v1/recommend/" + recommendationId).body().get("savedFriendId").asLong())
			.isEqualTo(friendId);
	}

	@Test
	void 다른_친구로의_재귀속은_400이다() {
		long recommendationId = 추천을_만든다().body().get("recommendationId").asLong();
		long friendId = 친구_101의_id();

		assertThat(post("/api/v1/recommend/" + recommendationId + "/save", "{\"friendId\":" + friendId + "}").status())
			.isEqualTo(200);

		long otherFriendId = 새_친구를_만든다();
		assertThat(post("/api/v1/recommend/" + recommendationId + "/save", "{\"friendId\":" + otherFriendId + "}")
			.status()).isEqualTo(400);
	}

	@Test
	void 남의_추천_ID_재조회는_404다() {
		long recommendationId = 추천을_만든다().body().get("recommendationId").asLong();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		// 없는 추천과 같은 404임 — 존재 여부를 알려주면 남의 ID를 탐색할 수 있음
		assertThat(get("/api/v1/recommend/" + recommendationId).status()).isEqualTo(404);
	}

	@Test
	void 추천_ID를_실어_보낸_선물은_200이고_남의_추천_ID는_400이다() {
		// 한 추천에서 id와 상품을 함께 꺼냄. 추천을 두 번 만들어 서로 다른 추천의 값을 섞으면
		// FIX-W004 ③의 대조 검증에 걸린다(같은 조건이라 우연히 통과해 오던 자리임)
		var created = 추천을_만든다().body();
		long recommendationId = created.get("recommendationId").asLong();
		long productId = created.get("results").get(0).get("product").get("id").asLong();

		String withRecommendation = "{\"productId\":" + productId + ",\"recommendationId\":" + recommendationId
				+ ",\"letterBody\":\"고마워\",\"friendName\":\"친구 2\"}";
		assertThat(post("/api/v1/gift", withRecommendation).status()).isEqualTo(200);

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		String othersRecommendation = "{\"productId\":" + productId + ",\"recommendationId\":" + recommendationId
				+ ",\"letterBody\":\"고마워\",\"friendName\":\"아무개\"}";
		assertThat(post("/api/v1/gift", othersRecommendation).status()).isEqualTo(400);
	}

	/**
	 * FIX-W004 ③: recommendationId를 실으면 productId가 **그 추천 결과 안에** 있어야 함. 소유만 보던 시절에는 내 추천
	 * id 하나로 카탈로그의 아무 상품이나 "추천으로 고른 선물"로 실을 수 있었음.
	 */
	@Test
	void 그_추천에_없는_상품을_추천_ID와_함께_보내면_400이다() {
		var created = 추천을_만든다().body();
		long recommendationId = created.get("recommendationId").asLong();

		java.util.List<Long> recommended = new java.util.ArrayList<>();
		created.get("results").forEach((item) -> recommended.add(item.get("product").get("id").asLong()));

		// 추천 3건 밖의 상품 하나를 시드 카탈로그에서 고름
		long outsider = -1;
		for (long candidate = 1; candidate <= 10; candidate++) {
			if (!recommended.contains(candidate)) {
				outsider = candidate;
				break;
			}
		}
		assertThat(outsider).as("시드 카탈로그가 추천 3건보다 커야 이 테스트가 성립함").isPositive();

		var response = post("/api/v1/gift", "{\"productId\":" + outsider + ",\"recommendationId\":" + recommendationId
				+ ",\"letterBody\":\"고마워\",\"friendName\":\"친구 2\"}");
		assertThat(response.status()).as(response.text()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("REC400_3");
	}

	/**
	 * FIX-W004 ②: 받은 편지함에 초대 토큰을 싣지 않음. 토큰은 양도 가능한 자격이고 `/g/{token}`이 비인증 경로라, 발송자가 번호를
	 * 오타 내 엉뚱한 회원이 수신자로 매칭되면 그 회원이 링크를 넘겨 임의의 제3자가 편지 전문과 사진을 열 수 있음.
	 *
	 * 발송분(`sent`)의 토큰은 남김 — 발송자가 자기가 보낸 링크를 받는 정상 경로이고 URL 복사가 그 값을 씀.
	 */
	@Test
	void 받은_편지함에는_초대_토큰이_실리지_않고_보낸_편지함에는_실린다() {
		선물을_보낸다("친구 2");

		assertThat(get("/api/v1/letters").body().get("sent").get(0).has("token")).as("발송분 토큰은 F12 URL 복사가 씀").isTrue();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		var received = get("/api/v1/letters").body().get("received");
		assertThat(received).isNotEmpty();
		assertThat(received.get(0).has("token")).as("수신분에 토큰이 실리면 편지 전문이 제3자에게 열림").isFalse();
	}

	/** 편지함과 초대 조회의 상품 표시 계약임(F29). 프론트가 `productId`로 이미지를 고르므로 id가 빠지면 화면이 빔. */
	@Test
	void 편지함과_초대_조회는_productId를_주고_emoji를_주지_않는다() {
		String token = 선물을_보낸다("친구 2").body().get("token").asString();

		var sent = get("/api/v1/letters").body().get("sent").get(0);
		assertThat(sent.get("productId").asLong()).isPositive();
		assertThat(sent.has("emoji")).isFalse();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		var received = get("/api/v1/letters").body().get("received").get(0);
		assertThat(received.get("productId").asLong()).isPositive();
		assertThat(received.has("emoji")).isFalse();

		post("/api/v1/g/" + token, null);
		var product = get("/api/v1/g/" + token).body().get("product");
		assertThat(product.get("productId").asLong()).isPositive();
		assertThat(product.has("emoji")).isFalse();
	}

	private Response 선물을_보낸다(String friendName) {
		long productId = 추천을_만든다().body().get("results").get(0).get("product").get("id").asLong();

		Response sent = post("/api/v1/gift",
				"{\"productId\":" + productId + ",\"letterBody\":\"생일 축하해\",\"friendName\":\"" + friendName + "\"}");
		assertThat(sent.status()).isEqualTo(200);
		return sent;
	}

	private Response 추천을_만든다() {
		Response created = post("/api/v1/recommend", "{\"relation\":\"연인\",\"minBudget\":50,\"maxBudget\":150}");
		assertThat(created.status()).isEqualTo(200);
		return created;
	}

	private long 친구_101의_id() {
		var list = get("/api/v1/friends").body().get("list");
		for (var friend : list) {
			if ("친구 2".equals(friend.get("name").asString())) {
				return friend.get("id").asLong();
			}
		}
		throw new AssertionError("시드 친구 101을 찾지 못함");
	}

	private long 새_친구를_만든다() {
		String phone = "010" + String.format("%08d", Math.abs(System.nanoTime() % 100000000L));
		Response created = post("/api/v1/friends", "{\"name\":\"다른친구\",\"phone\":\"" + phone + "\"}");
		assertThat(created.status()).isEqualTo(200);
		return created.body().get("friend").get("id").asLong();
	}

	/**
	 * 수신함의 선물을 익명 닉네임으로 식별함. 초대 토큰으로 식별하던 것을 FIX-W004 ②에서 바꿨음 — 받은 편지함 응답에 토큰이 실리면 오귀속된
	 * 회원이 그것을 넘겨 임의의 제3자가 편지 전문과 사진을 열 수 있음. 닉네임은 선물마다 새로 발급되므로 식별에 충분함.
	 */
	private java.util.List<String> nicknamesOf(Response letters) {
		java.util.List<String> nicknames = new java.util.ArrayList<>();
		letters.body().get("received").forEach((item) -> nicknames.add(item.get("nickname").asString()));
		return nicknames;
	}

}
