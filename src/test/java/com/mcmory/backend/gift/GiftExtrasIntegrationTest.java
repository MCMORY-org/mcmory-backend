package com.mcmory.backend.gift;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-08-09에 채운 미구현 요구사항 넷임 — FR-020 선물 자동 등록, FR-018 옵션 변경 문의, FR-012 편지 사진 계약, FR-005는
 * 친구 도메인이라 InputGuardIntegrationTest에 있음.
 *
 * 시드 전제: 회원 1(01012345678)이 발송자, 회원 2(01099998888)가 수신자이고 friend 101(친구 2)의 번호가 회원 2와 같음.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiftExtrasIntegrationTest extends HttpIntegrationSupport {

	private static final String SENDER_PHONE = "01012345678";

	private static final String RECIPIENT_PHONE = "01099998888";

	private static final String PASSWORD = "1234";

	/**
	 * 실제로 열리는 1x1 PNG임. 매직 넘버만 흉내 낸 바이트를 쓰면 "저장은 됐는데 화면에서 안 열리는" 상태를 통과시키게 됨.
	 */
	private static final byte[] PNG_1X1 = Base64.getDecoder()
		.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

	@BeforeEach
	void 발송자로_로그인() {
		clearCookies();
		loginAs(SENDER_PHONE, PASSWORD);
	}

	@Test
	void 열어본_선물은_내_제품으로_등록되고_재클릭해도_늘지_않는다() {
		String token = 선물을_보낸다();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);
		post("/api/v1/invitations/" + token, null);

		Response first = post("/api/v1/invitations/" + token + "/owned", null);
		assertThat(first.status()).as(first.text()).isEqualTo(200);
		assertThat(first.body().get("source").asString()).isEqualTo("GIFT");
		long ownedId = first.body().get("id").asLong();

		int countAfterFirst = get("/api/v1/owned").body().get("list").size();

		// 더블클릭 재현임. 새 행을 만들면 목록이 늘어남
		Response second = post("/api/v1/invitations/" + token + "/owned", null);
		assertThat(second.status()).isEqualTo(200);
		assertThat(second.body().get("id").asLong()).isEqualTo(ownedId);
		assertThat(get("/api/v1/owned").body().get("list").size()).isEqualTo(countAfterFirst);
	}

	@Test
	void 열기_전_등록은_400이고_비로그인_등록은_401이다() {
		String token = 선물을_보낸다();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);
		Response beforeOpen = post("/api/v1/invitations/" + token + "/owned", null);
		assertThat(beforeOpen.status()).isEqualTo(400);
		assertThat(beforeOpen.code()).isEqualTo("GIFT400_3");

		// 이 경로는 시큐리티가 인가하지 않으므로 컨트롤러의 requireId()가 유일한 문지기임.
		// 그것이 빠지면 남의 선물을 아무나 자기 제품으로 등록하게 되므로 이 단언이 인증 구멍의 감시자임
		post("/api/v1/invitations/" + token, null);
		clearCookies();
		Response anonymous = post("/api/v1/invitations/" + token + "/owned", null);
		assertThat(anonymous.status()).isEqualTo(401);
		// 상태 코드만 보면 봉투가 깨진 응답도 통과함. 화면은 문구가 아니라 code로 분기함
		assertThat(anonymous.code()).as(anonymous.text()).isEqualTo("AUTH401_1");
	}

	@Test
	void 남에게_지정된_선물은_없는_초대로_보인다() {
		String token = 선물을_보낸다();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);
		post("/api/v1/invitations/" + token, null);

		// 발송자는 이 선물의 수신자가 아님
		clearCookies();
		loginAs(SENDER_PHONE, PASSWORD);
		Response response = post("/api/v1/invitations/" + token + "/owned", null);

		assertThat(response.status()).isEqualTo(404);
		assertThat(response.code()).isEqualTo("GIFT404_1");
	}

	@Test
	void 옵션_변경_문의는_한_번만_되고_발송자_알림에_사유가_실린다() {
		String token = 선물을_보낸다();
		post("/api/v1/invitations/" + token, null);

		Response first = post("/api/v1/invitations/" + token + "/change-request", "{\"reason\":\"SIZE\"}");
		assertThat(first.status()).as(first.text()).isEqualTo(200);
		assertThat(first.body().get("reason").asString()).isEqualTo("SIZE");

		Response second = post("/api/v1/invitations/" + token + "/change-request", "{\"reason\":\"COLOR\"}");
		assertThat(second.status()).isEqualTo(409);
		assertThat(second.code()).isEqualTo("GIFT409_1");

		// 사유는 별도 컬럼이 아니라 알림 타입에 실림. 발송자가 그것을 읽는 것이 요구사항의 핵심임
		boolean hasReason = false;
		for (var item : get("/api/v1/notifications").body().get("items")) {
			hasReason = hasReason || "CHANGE_REQ_SIZE".equals(item.get("type").asString());
		}
		assertThat(hasReason).isTrue();
	}

	@Test
	void 모르는_문의_사유는_400이다() {
		String token = 선물을_보낸다();
		post("/api/v1/invitations/" + token, null);

		Response response = post("/api/v1/invitations/" + token + "/change-request", "{\"reason\":\"WHATEVER\"}");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("GIFT400_4");
	}

	@Test
	void 우리가_발급하지_않은_사진_URL은_선물에_실을_수_없다() {
		// 임의 URL을 허용하면 수신자 화면이 외부 이미지를 그대로 그리게 됨
		long productId = 상품_id();
		Response response = post("/api/v1/gift", "{\"productId\":" + productId
				+ ",\"letterBody\":\"사진 검증\",\"friendName\":\"친구 2\",\"letterImageUrls\":[\"https://example.com/x.png\"]}");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("GIFT400_7");
	}

	@Test
	void 접두사만_맞춘_경로_조작_URL은_거부된다() {
		// startsWith 검사만 하면 통과하는 값들임. 소비자가 dot-segment를 정규화하면 폴더 밖 경로를 부르게 됨
		long productId = 상품_id();
		for (String url : new String[] { "/letter-images/../api/v1/auth/me", "/letter-images/%2e%2e/api/v1/auth/me",
				"/letter-images/", "/letter-images/없는파일.jpg", "/letter-images/00000000000000000000000000000000.jpg" }) {
			Response response = post("/api/v1/gift", "{\"productId\":" + productId
					+ ",\"letterBody\":\"경로 검증\",\"friendName\":\"친구 2\",\"letterImageUrls\":[\"" + url + "\"]}");

			assertThat(response.status()).as(url).isEqualTo(400);
			assertThat(response.code()).as(url).isEqualTo("GIFT400_7");
		}
	}

	@Test
	void 사진_없이_보낸_선물은_열람에_사진_키가_없다() {
		String token = 선물을_보낸다();
		post("/api/v1/invitations/" + token, null);

		Response invite = get("/api/v1/invitations/" + token);

		assertThat(invite.status()).isEqualTo(200);
		assertThat(invite.body().has("letterImageUrls")).as(invite.text()).isFalse();
	}

	@Test
	void 올린_사진이_발송과_열람까지_그대로_실린다() {
		// 시연에서 실기기 사진이 지나가는 경로 전체임. 업로드가 발급한 URL만 발송에 실릴 수 있으므로
		// 이 한 건이 업로드와 저장과 URL 검증(실물 존재 확인 포함)과 열람 노출을 한 번에 덮음
		Response uploaded = postFiles("/api/v1/gift/letter-images", "image/png", List.of(PNG_1X1, PNG_1X1));
		assertThat(uploaded.status()).as(uploaded.text()).isEqualTo(200);
		assertThat(uploaded.body().get("urls").size()).isEqualTo(2);

		String first = uploaded.body().get("urls").get(0).asString();
		assertThat(first).matches("^/letter-images/[0-9a-f]{32}\\.png$");

		String second = uploaded.body().get("urls").get(1).asString();
		Response sent = post("/api/v1/gift",
				"{\"productId\":" + 상품_id()
						+ ",\"letterBody\":\"사진 왕복\",\"friendName\":\"친구 2\",\"letterImageUrls\":[\"" + first + "\",\""
						+ second + "\"]}");
		assertThat(sent.status()).as(sent.text()).isEqualTo(200);

		String token = sent.body().get("token").asString();
		post("/api/v1/invitations/" + token, null);
		Response invite = get("/api/v1/invitations/" + token);

		assertThat(invite.body().get("letterImageUrls").size()).isEqualTo(2);
		assertThat(invite.body().get("letterImageUrls").get(0).asString()).isEqualTo(first);
	}

	@Test
	void 선언한_형식과_다른_바이트는_거부된다() {
		// image/png라고 선언하고 텍스트를 보냄. **매직 넘버 검사를 통째로 지웠을 때의 감시자임** —
		// "선언 형식과 일치하는가"로 조인 것을 되돌려도 이건 통과하므로, 그 회귀를 잡는 것은 아래 교차 MIME 테스트임
		byte[] notAnImage = "이건 이미지가 전혀 아니다".getBytes(StandardCharsets.UTF_8);
		Response response = postFiles("/api/v1/gift/letter-images", "image/png", List.of(notAnImage));

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("GIFT400_7");
	}

	@Test
	void 선언한_형식과_다른_이미지도_거부된다() {
		// JPEG 바이트를 image/png로 선언함. 확장자를 선언 MIME으로 정하므로 "아무 이미지인가"만 보면 .png로 저장돼버림.
		// **이것이 matchesDeclaredType의 진짜 감시자다** — 되돌리면 200이 나와 빨개짐
		byte[] jpegHead = new byte[16];
		jpegHead[0] = (byte) 0xFF;
		jpegHead[1] = (byte) 0xD8;
		jpegHead[2] = (byte) 0xFF;
		Response response = postFiles("/api/v1/gift/letter-images", "image/png", List.of(jpegHead));

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("GIFT400_7");
	}

	@Test
	void 사진_여섯_장은_거부된다() {
		Response response = postFiles("/api/v1/gift/letter-images", "image/png",
				List.of(PNG_1X1, PNG_1X1, PNG_1X1, PNG_1X1, PNG_1X1, PNG_1X1));

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("GIFT400_6");
	}

	@Test
	void 상한을_넘는_사진은_연결이_끊기지_않고_400_봉투로_돌아온다() {
		// 서블릿 상한(10MB)이 서비스 검증보다 먼저 자르므로 MaxUploadSizeExceededException 핸들러가 유일한 방어선임.
		// 톰캣이 남은 본문을 버리지 않고 끊어버리면 프론트가 봉투 대신 연결 오류를 보게 되므로 그 경계를 함께 확인함
		byte[] tooLarge = new byte[11 * 1024 * 1024];
		System.arraycopy(PNG_1X1, 0, tooLarge, 0, PNG_1X1.length);
		Response response = postFiles("/api/v1/gift/letter-images", "image/png", List.of(tooLarge));

		assertThat(response.status()).as(response.text()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("GIFT400_7");
	}

	@Test
	void 파일이_없으면_500이_아니라_400이다() {
		// 파트가 없으면 컨트롤러에 도달하지 못해 COMMON500으로 새던 지점임(required=false로 서비스까지 흘려보냄)
		Response response = postEmptyMultipart("/api/v1/gift/letter-images");

		assertThat(response.status()).as(response.text()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("GIFT400_5");
	}

	@Test
	void 색상은_동의_후에만_보이고_팔레트_밖이면_400이다() {
		// 자유 hex는 v1.1 팔레트 확정 후 거부 대상임(디자인에 없는 색이 들어오는 통로였음)
		Response hex = post("/api/v1/gift", "{\"productId\":" + 상품_id()
				+ ",\"letterBody\":\"색상 검증\",\"friendName\":\"친구 2\",\"letterColor\":\"#F4C2C2\"}");
		assertThat(hex.status()).isEqualTo(400);
		assertThat(hex.code()).isEqualTo("GIFT400_8");

		Response invalid = post("/api/v1/gift", "{\"productId\":" + 상품_id()
				+ ",\"letterBody\":\"색상 검증\",\"friendName\":\"친구 2\",\"letterColor\":\"red\"}");
		assertThat(invalid.status()).isEqualTo(400);
		assertThat(invalid.code()).isEqualTo("GIFT400_8");

		Response sent = post("/api/v1/gift", "{\"productId\":" + 상품_id()
				+ ",\"letterBody\":\"색상 검증\",\"friendName\":\"친구 2\",\"letterColor\":\"beige\"}");
		assertThat(sent.status()).as(sent.text()).isEqualTo(200);
		String token = sent.body().get("token").asString();

		// 동의 전에는 키 자체가 없어야 함(NON_NULL 계약)
		Response before = get("/api/v1/invitations/" + token);
		assertThat(before.body().has("letterColor")).as(before.text()).isFalse();

		post("/api/v1/invitations/" + token, null);
		Response after = get("/api/v1/invitations/" + token);
		// 소문자로 보내도 대문자 토큰으로 정규화해 저장함
		assertThat(after.body().get("letterColor").asString()).isEqualTo("BEIGE");
	}

	/**
	 * FIX-W004 ①: 번호 없는 친구에게 보낸 선물은 발송 시점 전화번호 매칭이 없어 `recipientMemberId`가 비어 있음. 수신자가
	 * 실제로 열고 등록하면 그때 귀속해야 받은 편지함이 참. 등록이 유일한 인증 지점임(동의와 열람은 비인증 토큰 경로).
	 */
	@Test
	void 번호_없는_친구의_선물도_등록하면_받은_편지함에_잡힌다() {
		long productId = 상품_id();
		Response sent = post("/api/v1/gift",
				"{\"productId\":" + productId + ",\"letterBody\":\"검증용 편지\",\"friendName\":\"번호없는친구\"}");
		assertThat(sent.status()).as(sent.text()).isEqualTo(200);
		String token = sent.body().get("token").asString();
		String nickname = sent.body().get("nickname").asString();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);

		// 등록 전에는 수신함에 없음 — 발송 시점에 귀속될 근거가 없었음
		assertThat(nicknamesOf(get("/api/v1/letters"))).doesNotContain(nickname);

		post("/api/v1/invitations/" + token, null);
		assertThat(post("/api/v1/invitations/" + token + "/owned", null).status()).isEqualTo(200);

		assertThat(nicknamesOf(get("/api/v1/letters"))).as("등록했는데 받은 편지함이 비어 있음").contains(nickname);
	}

	private java.util.List<String> nicknamesOf(Response letters) {
		java.util.List<String> nicknames = new java.util.ArrayList<>();
		letters.body().get("received").forEach((item) -> nicknames.add(item.get("nickname").asString()));
		return nicknames;
	}

	/** 보유 제품의 상품 표시 계약임(F29). 프론트가 `productId`로 이미지를 고르므로 id가 빠지면 화면이 빔. */
	@Test
	void 보유_등록과_목록은_productId를_주고_emoji를_주지_않는다() {
		String token = 선물을_보낸다();

		clearCookies();
		loginAs(RECIPIENT_PHONE, PASSWORD);
		post("/api/v1/invitations/" + token, null);

		Response registered = post("/api/v1/invitations/" + token + "/owned", null);
		assertThat(registered.status()).as(registered.text()).isEqualTo(200);
		assertThat(registered.body().get("product").get("productId").asLong()).isPositive();
		assertThat(registered.body().get("product").has("emoji")).isFalse();

		var listed = get("/api/v1/owned").body().get("list").get(0).get("product");
		assertThat(listed.get("productId").asLong()).isPositive();
		assertThat(listed.has("emoji")).isFalse();
	}

	private long 상품_id() {
		Response recommended = post("/api/v1/recommend", "{\"relation\":\"친구\",\"minBudget\":0,\"maxBudget\":500}");
		assertThat(recommended.status()).isEqualTo(200);
		return recommended.body().get("results").get(0).get("product").get("id").asLong();
	}

	private String 선물을_보낸다() {
		Response sent = post("/api/v1/gift",
				"{\"productId\":" + 상품_id() + ",\"letterBody\":\"검증용 편지\",\"friendName\":\"친구 2\"}");
		assertThat(sent.status()).as(sent.text()).isEqualTo(200);
		return sent.body().get("token").asString();
	}

}
