package com.mcmory.backend.taste;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FEAT-W001 `Start-02` 설문 제출임. 제출한 답이 추천을 실제로 바꾸는지가 요점임.
 *
 * 시드 근거: 기본 조건(친구, 50에서 150만원)에서 취향이 없으면 1순위가 상품 6이고, 블랙을 답하면 상품 1, 핑크를 답하면 상품 8임.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SurveySubmitIntegrationTest extends HttpIntegrationSupport {

	private static final String DEFAULT_BUDGET = "\"minBudget\":50,\"maxBudget\":150";

	@BeforeEach
	void 로그인() {
		clearCookies();
		loginAs("01012345678", "1234");
	}

	/** 친구를 만들고 설문 토큰을 받음. 번호가 겹치면 DUPLICATE_PHONE이라 호출마다 다른 번호를 씀. */
	private String 설문토큰발급(String name, String phone) {
		var created = post("/api/v1/friends", "{\"name\":\"" + name + "\",\"phone\":\"" + phone + "\"}");
		assertThat(created.status()).as(created.text()).isEqualTo(200);

		long friendId = created.body().get("friend").get("id").asLong();
		var issued = post("/api/v1/friends/" + friendId + "/survey", null);
		assertThat(issued.status()).as(issued.text()).isEqualTo(200);

		return issued.body().get("token").asString();
	}

	@Test
	void 수신자_답변이_추천_1순위를_바꾼다() {
		String token = 설문토큰발급("설문친구", "01055550001");

		var view = get("/api/v1/s/" + token);
		assertThat(view.status()).as(view.text()).isEqualTo(200);
		assertThat(view.body().get("friendName").asString()).isEqualTo("설문친구");
		assertThat(view.body().get("answered").asBoolean()).isFalse();

		var submitted = post("/api/v1/s/" + token,
				"{\"privacyAgreed\":true,\"colors\":[\"블랙\"],\"styles\":[\"미니멀\"],\"bags\":[\"크로스바디\"]}");
		assertThat(submitted.status()).as(submitted.text()).isEqualTo(200);
		assertThat(get("/api/v1/s/" + token).body().get("answered").asBoolean()).isTrue();

		long friendId = 설문친구아이디("설문친구");
		var recommended = post("/api/v1/recommend",
				"{\"relation\":\"친구\",\"friendId\":" + friendId + "," + DEFAULT_BUDGET + "}");

		var first = recommended.body().get("results").get(0);
		// 상품 id를 못박음. "달라졌다"로만 두면 다른 블랙 상품으로 바뀌어도 통과해 회귀를 놓침
		assertThat(first.get("product").get("id").asLong()).as("설문 답변이 추천에 반영되지 않음: " + recommended.text())
			.isEqualTo(1L);
		assertThat(first.get("reasonType").asString()).isEqualTo("PERSONAL");
		assertThat(first.get("reason").asString()).contains("블랙");
	}

	/** 발송자 대리 입력이 수신자 본인 답변을 덮으면 추천 근거가 조용히 사라짐(ADR-009 결정 2). */
	@Test
	void 제출된_답변은_발송자의_취향_저장으로_덮이지_않는다() {
		String token = 설문토큰발급("보호친구", "01055550002");
		post("/api/v1/s/" + token, "{\"privacyAgreed\":true,\"colors\":[\"블랙\"],\"styles\":[],\"bags\":[]}");

		long friendId = 설문친구아이디("보호친구");
		var saved = patch("/api/v1/friends", "{\"id\":" + friendId + ",\"tasteSummary\":\"발송자가 적은 요약\"}");

		assertThat(saved.status()).as(saved.text()).isEqualTo(200);
		assertThat(saved.body().get("updated").asBoolean()).isFalse();
		assertThat(saved.body().get("reason").asString()).isEqualTo("INVITE_ANSWER_PROTECTED");
	}

	@Test
	void 재제출은_행을_늘리지_않고_덮어쓴다() {
		String token = 설문토큰발급("재제출친구", "01055550003");

		var first = post("/api/v1/s/" + token,
				"{\"privacyAgreed\":true,\"colors\":[\"블랙\"],\"styles\":[],\"bags\":[]}");
		var second = post("/api/v1/s/" + token,
				"{\"privacyAgreed\":true,\"colors\":[\"핑크\"],\"styles\":[],\"bags\":[]}");

		assertThat(first.status()).as(first.text()).isEqualTo(200);
		assertThat(second.status()).as(second.text()).isEqualTo(200);

		long friendId = 설문친구아이디("재제출친구");
		var recommended = post("/api/v1/recommend",
				"{\"relation\":\"친구\",\"friendId\":" + friendId + "," + DEFAULT_BUDGET + "}");

		// 나중 답변이 이겨야 함. 상품 8이 유일한 핑크임
		assertThat(recommended.body().get("results").get(0).get("product").get("id").asLong())
			.as("재제출이 반영되지 않음: " + recommended.text())
			.isEqualTo(8L);
	}

	@Test
	void 링크_발급은_멱등이라_같은_토큰을_돌려준다() {
		var created = post("/api/v1/friends", "{\"name\":\"멱등친구\",\"phone\":\"01055550004\"}");
		long friendId = created.body().get("friend").get("id").asLong();

		String first = post("/api/v1/friends/" + friendId + "/survey", null).body().get("token").asString();
		String second = post("/api/v1/friends/" + friendId + "/survey", null).body().get("token").asString();

		// 재발급으로 토큰이 바뀌면 발송자가 이미 문자로 보낸 링크가 조용히 죽음
		assertThat(second).isEqualTo(first);
		assertThat(first).hasSize(24).matches("^[a-z0-9]+$");
	}

	@Test
	void 남의_친구에게는_설문_링크를_발급하지_못한다() {
		var created = post("/api/v1/friends", "{\"name\":\"남의친구\",\"phone\":\"01055550005\"}");
		long friendId = created.body().get("friend").get("id").asLong();

		clearCookies();
		loginAs("01099998888", "1234");
		var issued = post("/api/v1/friends/" + friendId + "/survey", null);

		// 없는 친구와 남의 친구를 같은 코드로 다룸 — 순번을 훑어 존재 여부를 알아내는 것을 막음
		assertThat(issued.status()).as(issued.text()).isEqualTo(404);
		assertThat(issued.code()).isEqualTo("FRIEND404_1");
	}

	@Test
	void 없는_토큰은_404다() {
		var view = get("/api/v1/s/nosuchtokennosuchtoken");
		var submitted = post("/api/v1/s/nosuchtokennosuchtoken",
				"{\"privacyAgreed\":true,\"colors\":[\"블랙\"],\"styles\":[],\"bags\":[]}");

		assertThat(view.status()).as(view.text()).isEqualTo(404);
		assertThat(submitted.status()).as(submitted.text()).isEqualTo(404);
		assertThat(submitted.code()).isEqualTo("FRIEND404_1");
	}

	@Test
	void 삭제된_친구의_설문_링크는_열리지_않는다() {
		var created = post("/api/v1/friends", "{\"name\":\"삭제친구\",\"phone\":\"01055550006\"}");
		long friendId = created.body().get("friend").get("id").asLong();
		String token = post("/api/v1/friends/" + friendId + "/survey", null).body().get("token").asString();

		var erased = delete("/api/v1/friends", "{\"id\":" + friendId + "}");
		assertThat(erased.status()).as(erased.text()).isEqualTo(200);

		assertThat(get("/api/v1/s/" + token).status()).isEqualTo(404);
	}

	@Test
	void 동의를_안_하면_저장하지_않는다() {
		String token = 설문토큰발급("동의친구", "01055550007");

		var submitted = post("/api/v1/s/" + token,
				"{\"privacyAgreed\":false,\"colors\":[\"블랙\"],\"styles\":[],\"bags\":[]}");

		assertThat(submitted.status()).as(submitted.text()).isEqualTo(400);
		assertThat(submitted.code()).isEqualTo("FRIEND400_3");
		assertThat(get("/api/v1/s/" + token).body().get("answered").asBoolean()).isFalse();
	}

	@Test
	void 선택지_밖_값과_빈_답변은_400이다() {
		String token = 설문토큰발급("검증친구", "01055550008");

		var outOfList = post("/api/v1/s/" + token,
				"{\"privacyAgreed\":true,\"colors\":[\"형광연두\"],\"styles\":[],\"bags\":[]}");
		// null 원소가 NPE로 500이 되면 사용자 입력 오류가 서버 오류로 둔갑함
		var withNull = post("/api/v1/s/" + token,
				"{\"privacyAgreed\":true,\"colors\":[null,\"블랙\"],\"styles\":[],\"bags\":[]}");
		// 가방만 고르면 답변은 있는데 추천에 아무 영향이 없는 상태가 됨
		var bagsOnly = post("/api/v1/s/" + token,
				"{\"privacyAgreed\":true,\"colors\":[],\"styles\":[],\"bags\":[\"백팩\"]}");

		assertThat(outOfList.status()).as(outOfList.text()).isEqualTo(400);
		assertThat(outOfList.code()).isEqualTo("FRIEND400_4");
		assertThat(bagsOnly.status()).as(bagsOnly.text()).isEqualTo(400);
		assertThat(bagsOnly.code()).isEqualTo("FRIEND400_4");
		assertThat(withNull.status()).as(withNull.text()).isEqualTo(400);
		assertThat(withNull.code()).isEqualTo("FRIEND400_4");
	}

	/** 이름으로 친구 id를 되찾음. */
	private long 설문친구아이디(String name) {
		for (var friend : get("/api/v1/friends").body().get("list")) {
			if (name.equals(friend.get("name").asString())) {
				return friend.get("id").asLong();
			}
		}
		throw new IllegalStateException("친구를 찾지 못함: " + name);
	}

}
