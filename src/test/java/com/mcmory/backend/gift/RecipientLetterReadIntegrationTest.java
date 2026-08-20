package com.mcmory.backend.gift;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-W006 인증된 수신자 편지 열람임. `GET /api/v1/letters/{giftId}`가 계약이고 명세서 5.4의 #37임.
 *
 * 이 경로가 없던 시절에는 수신자가 자기 편지 본문을 가져올 방법이 아예 없었음 — 목록(#14)이 본문도 토큰도 주지 않기 때문임. 화면은 그 공백을 더미로
 * 메우고 있었고 빈 본문과 무관한 사진이 그대로 보였음.
 *
 * **인가는 시큐리티 경로 규칙이 아니라 API마다 검사함**(SecurityConfig가 anyRequest().permitAll()임). 아래 미인증
 * 401과 남의 편지 404 두 테스트가 그 인가 회귀를 잡는 그물임 — 지우지 말 것.
 *
 * 시드 전제: 회원 1(테스터, 01012345678)이 발송자, 회원 2(수신자, 01099998888)가 수신자임. friend 101(친구 2)의
 * 전화번호가 회원 2와 같아 회원 수신자 경로가 성립함.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipientLetterReadIntegrationTest extends HttpIntegrationSupport {

	private static final String SENDER_PHONE = "01012345678";

	private static final String RECIPIENT_PHONE = "01099998888";

	private static final String PASSWORD = "1234";

	@BeforeEach
	void 발송자로_로그인() {
		clearCookies();
		loginAs(SENDER_PHONE, PASSWORD);
	}

	@Test
	void 수신자는_동의_후_자기_편지_본문을_받는다() {
		String token = 선물을_보낸다().body().get("token").asString();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);
		long giftId = 내_수신함_첫_선물의_id();

		// 최초 개봉은 토큰 경로 전용임(FIX-W006 3장) — 회원 경로에는 쓰기가 없음
		assertThat(post("/api/v1/invitations/" + token, null).status()).isEqualTo(200);

		var read = get("/api/v1/letters/" + giftId);
		assertThat(read.status()).isEqualTo(200);
		assertThat(read.body().get("needConsent").asBoolean()).isFalse();
		assertThat(read.body().get("letterBody").asString()).isEqualTo("생일 축하해");
		assertThat(read.body().get("product").get("name").asString()).isNotEmpty();
		assertThat(read.body().get("openedAt").isNull()).isFalse();
	}

	@Test
	void 동의_전에는_본문_키_자체가_없다() {
		선물을_보낸다();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		// FR-015 동의 게이트임. 회원이라는 사실이 면제 사유가 아니고, NON_NULL 계약이라 키가 아예 없어야 함
		var read = get("/api/v1/letters/" + 내_수신함_첫_선물의_id());
		assertThat(read.status()).isEqualTo(200);
		assertThat(read.body().get("needConsent").asBoolean()).isTrue();
		assertThat(read.body().has("letterBody")).isFalse();
		assertThat(read.body().has("product")).isFalse();
		assertThat(read.body().get("nickname").asString()).contains("호저");
	}

	@Test
	void 남의_편지_id_조회는_404다() {
		선물을_보낸다();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);
		long giftId = 내_수신함_첫_선물의_id();

		clearCookies();
		loginAs(SENDER_PHONE, PASSWORD);

		// 발송자는 수신자가 아님. 없는 id와 같은 404라 존재 여부를 알려주지 않음
		var read = get("/api/v1/letters/" + giftId);
		assertThat(read.status()).isEqualTo(404);
		assertThat(read.code()).isEqualTo("GIFT404_1");
		assertThat(get("/api/v1/letters/99999999").status()).isEqualTo(404);
	}

	@Test
	void 미인증_조회는_401이다() {
		선물을_보낸다();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);
		long giftId = 내_수신함_첫_선물의_id();

		clearCookies();

		// SecurityConfig가 permitAll이라 컨트롤러가 requireId를 부르지 않으면 남의 편지가 그대로 열림
		var read = get("/api/v1/letters/" + giftId);
		assertThat(read.status()).isEqualTo(401);
		assertThat(read.code()).isEqualTo("AUTH401_1");
	}

	private long 내_수신함_첫_선물의_id() {
		var received = get("/api/v1/letters").body().get("received");
		assertThat(received).isNotEmpty();
		return received.get(0).get("id").asLong();
	}

	private Response 선물을_보낸다() {
		Response created = post("/api/v1/recommend", "{\"relation\":\"연인\",\"minBudget\":50,\"maxBudget\":150}");
		assertThat(created.status()).isEqualTo(200);
		long productId = created.body().get("results").get(0).get("product").get("id").asLong();

		Response sent = post("/api/v1/gift",
				"{\"productId\":" + productId + ",\"letterBody\":\"생일 축하해\",\"friendName\":\"친구 2\"}");
		assertThat(sent.status()).isEqualTo(200);
		return sent;
	}

}
