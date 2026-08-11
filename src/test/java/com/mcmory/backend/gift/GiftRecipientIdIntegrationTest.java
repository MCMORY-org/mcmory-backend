package com.mcmory.backend.gift;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-013 수신자 식별임(FIX-W001 T1).
 *
 * 배경: 발송 계약이 이름 문자열만 받아서 친구를 이름으로 찾았음. 그래서 이름을 한 글자만 틀려도 전화번호가 비어 있는 친구 행이 새로 생기고,
 * recipient_member_id 해석이 그 빈 번호로 실패해 **도착 알림이 조용히 끊겼음**. 화면(HOME-02)은 성함과 전화번호를 둘 다 필수로
 * 받는데 그 번호가 서버로 오지 않던 것이 원인임.
 *
 * 시드 전제: 회원 1(테스터, 01012345678)이 발송자, 회원 2(수신자, 01099998888)가 수신자임. friend 101(친구 2)의
 * 전화번호가 회원 2와 같아 회원 수신자 경로가 성립함.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiftRecipientIdIntegrationTest extends HttpIntegrationSupport {

	private static final String SENDER_PHONE = "01012345678";

	private static final String RECIPIENT_PHONE = "01099998888";

	private static final String PASSWORD = "1234";

	/** 시드가 심어둔 친구 행. 전화번호가 회원 2와 같음. */
	private static final long SEEDED_FRIEND_ID = 101L;

	@BeforeEach
	void 발송자로_로그인() {
		clearCookies();
		loginAs(SENDER_PHONE, PASSWORD);
	}

	/**
	 * 이 테스트가 결함의 본체임. friendId를 주면 이름이 무엇이든 그 친구에게 가야 하고, 그 친구의 번호가 회원과 맞으므로 도착 알림이 생겨야
	 * 함.
	 *
	 * friendId를 무시하던 시절에는 "친구 2"가 아닌 이름이 새 친구 행을 만들어 알림이 0건이었음.
	 */
	@Test
	void friendId를_주면_이름이_달라도_그_친구에게_가고_도착_알림이_생긴다() {
		// 수신함 식별은 익명 닉네임으로 함 — 받은 편지함에는 초대 토큰이 실리지 않음(FIX-W004 ②)
		String nickname = 선물을_보낸다("{\"friendId\":" + SEEDED_FRIEND_ID + ",\"friendName\":\"친구 둘\"}").body()
			.get("nickname")
			.asString();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		var letters = get("/api/v1/letters");
		assertThat(letters.status()).as(letters.text()).isEqualTo(200);
		assertThat(letters.text()).as("수신함에 이 선물이 있어야 함").contains(nickname);

		var notifications = get("/api/v1/notifications");
		assertThat(notifications.body().get("unread").asInt()).as(notifications.text()).isPositive();
		assertThat(notifications.text()).contains("GIFT_ARRIVED");
	}

	/** 남의 친구 ID로 보내는 것을 막음. 없는 ID와 남의 ID를 같은 404로 다뤄 존재 여부를 흘리지 않음. */
	@Test
	void 남의_친구_ID로는_보낼_수_없다() {
		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		long productId = 추천을_만든다().body().get("results").get(0).get("product").get("id").asLong();
		var sent = post("/api/v1/gift", "{\"productId\":" + productId + ",\"letterBody\":\"안녕\",\"friendId\":"
				+ SEEDED_FRIEND_ID + ",\"friendName\":\"친구 2\"}");

		assertThat(sent.status()).as(sent.text()).isEqualTo(404);
		assertThat(sent.code()).isEqualTo("FRIEND404_1");
	}

	/** 없는 친구 ID도 같은 404임. */
	@Test
	void 없는_친구_ID는_404다() {
		long productId = 추천을_만든다().body().get("results").get(0).get("product").get("id").asLong();
		var sent = post("/api/v1/gift",
				"{\"productId\":" + productId + ",\"letterBody\":\"안녕\",\"friendId\":99999,\"friendName\":\"아무개\"}");

		assertThat(sent.status()).as(sent.text()).isEqualTo(404);
		assertThat(sent.code()).isEqualTo("FRIEND404_1");
	}

	/** friendId 없이 이름만 보내는 기존 경로가 그대로 동작해야 함 — 데모와 스모크가 아직 이 형태임. */
	@Test
	void friendId가_없으면_이름_경로가_그대로_동작한다() {
		var sent = 선물을_보낸다("{\"friendName\":\"친구 2\"}");
		assertThat(sent.body().get("token").asString()).isNotBlank();
	}

	private Response 선물을_보낸다(String recipientFields) {
		long productId = 추천을_만든다().body().get("results").get(0).get("product").get("id").asLong();

		// recipientFields는 friendId와 friendName을 담은 JSON 조각임(앞뒤 중괄호 포함)
		String body = "{\"productId\":" + productId + ",\"letterBody\":\"생일 축하해\","
				+ recipientFields.substring(1, recipientFields.length() - 1) + "}";

		Response sent = post("/api/v1/gift", body);
		assertThat(sent.status()).as(sent.text()).isEqualTo(200);
		return sent;
	}

	private Response 추천을_만든다() {
		Response created = post("/api/v1/recommend", "{\"relation\":\"연인\",\"minBudget\":50,\"maxBudget\":150}");
		assertThat(created.status()).as(created.text()).isEqualTo(200);
		return created;
	}

}
