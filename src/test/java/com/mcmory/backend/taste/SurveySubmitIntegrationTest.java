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
		// 발송자 실명("테스터")이 새면 안 됨. 지금은 더미이고 표시명 규칙은 미결임(AnonNicknameProvider)
		assertThat(view.body().get("senderName").asString()).isEqualTo("멋쟁이사자");

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

	/** FEAT-W003 질문 선별임. 발송자가 켠 축만 수신자 화면에 나가야 함. */
	@Test
	void 켠_축만_설문에_나가고_끈_축의_답은_막힌다() {
		var created = post("/api/v1/friends", "{\"name\":\"선별친구\",\"phone\":\"01055550009\"}");
		long friendId = created.body().get("friend").get("id").asLong();

		var issued = post("/api/v1/friends/" + friendId + "/survey", "{\"axes\":[\"colors\"]}");
		assertThat(issued.status()).as(issued.text()).isEqualTo(200);
		String token = issued.body().get("token").asString();

		var view = get("/api/v1/s/" + token);
		assertThat(view.body().get("axes").toString()).as(view.text()).isEqualTo("[\"colors\"]");

		// 끈 축의 답을 조용히 버리면 수신자가 고른 것이 사라진 채 200이 나감
		var offAxis = post("/api/v1/s/" + token,
				"{\"privacyAgreed\":true,\"colors\":[\"블랙\"],\"styles\":[\"미니멀\"],\"bags\":[]}");
		assertThat(offAxis.status()).as(offAxis.text()).isEqualTo(400);
		assertThat(offAxis.code()).isEqualTo("FRIEND400_4");

		var onAxis = post("/api/v1/s/" + token,
				"{\"privacyAgreed\":true,\"colors\":[\"블랙\"],\"styles\":[],\"bags\":[]}");
		assertThat(onAxis.status()).as(onAxis.text()).isEqualTo(200);
		// 200만 보면 축 검사가 저장까지 통째로 건너뛰어도 통과함(CodeRabbit PR #18)
		assertThat(get("/api/v1/s/" + token).body().get("answered").asBoolean()).isTrue();
	}

	/** 토글을 고쳐 다시 저장하는 것이 정상 경로임 — 그때 이미 보낸 링크가 죽으면 안 됨. */
	@Test
	void 재발급은_토큰을_두고_축만_덮어쓴다() {
		var created = post("/api/v1/friends", "{\"name\":\"토글친구\",\"phone\":\"01055550010\"}");
		long friendId = created.body().get("friend").get("id").asLong();

		String first = post("/api/v1/friends/" + friendId + "/survey", "{\"axes\":[\"colors\"]}").body()
			.get("token")
			.asString();
		var second = post("/api/v1/friends/" + friendId + "/survey", "{\"axes\":[\"styles\",\"bags\"]}");

		assertThat(second.body().get("token").asString()).isEqualTo(first);
		assertThat(get("/api/v1/s/" + first).body().get("axes").toString()).isEqualTo("[\"styles\",\"bags\"]");
	}

	@Test
	void 본문이_없으면_세_축_전부다() {
		String token = 설문토큰발급("기본축친구", "01055550011");

		// 화면이 토글을 안 보내던 시절과 같게 동작해야 함. 여기서 축이 비면 기존 프론트가 아무것도 못 그림
		assertThat(get("/api/v1/s/" + token).body().get("axes").toString())
			.isEqualTo("[\"colors\",\"styles\",\"bags\"]");
	}

	/** 구형 화면의 재호출이 발송자의 선택을 세 축으로 되돌리면 안 됨(2026-08-11 codex 검토). */
	@Test
	void 축을_안_보낸_재발급은_저장된_선택을_지우지_않는다() {
		var created = post("/api/v1/friends", "{\"name\":\"보존친구\",\"phone\":\"01055550013\"}");
		long friendId = created.body().get("friend").get("id").asLong();

		post("/api/v1/friends/" + friendId + "/survey", "{\"axes\":[\"colors\"]}");
		var again = post("/api/v1/friends/" + friendId + "/survey", null);

		assertThat(again.body().get("axes").toString()).as(again.text()).isEqualTo("[\"colors\"]");
	}

	@Test
	void 색상과_스타일을_둘_다_끄면_발급이_400이다() {
		var created = post("/api/v1/friends", "{\"name\":\"가방만친구\",\"phone\":\"01055550012\"}");
		long friendId = created.body().get("friend").get("id").asLong();

		// 가방은 점수에 안 쓰여 답을 받아도 추천이 그대로임. 제출 단계의 같은 규칙을 발급 단계로 당긴 것임
		var bagsOnly = post("/api/v1/friends/" + friendId + "/survey", "{\"axes\":[\"bags\"]}");
		var unknown = post("/api/v1/friends/" + friendId + "/survey", "{\"axes\":[\"scent\"]}");
		// 빈 배열은 생략과 다름. 셋 다 끈 것을 세 축 전부로 되돌리면 발송자가 고른 것의 정반대가 됨
		var empty = post("/api/v1/friends/" + friendId + "/survey", "{\"axes\":[]}");
		// null 원소가 NPE로 500이 되면 사용자 입력 오류가 서버 오류로 둔갑함
		var withNull = post("/api/v1/friends/" + friendId + "/survey", "{\"axes\":[null,\"colors\"]}");

		assertThat(bagsOnly.status()).as(bagsOnly.text()).isEqualTo(400);
		assertThat(bagsOnly.code()).isEqualTo("FRIEND400_4");
		assertThat(unknown.status()).as(unknown.text()).isEqualTo(400);
		assertThat(unknown.code()).isEqualTo("FRIEND400_4");
		assertThat(empty.status()).as(empty.text()).isEqualTo(400);
		assertThat(empty.code()).isEqualTo("FRIEND400_4");
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
