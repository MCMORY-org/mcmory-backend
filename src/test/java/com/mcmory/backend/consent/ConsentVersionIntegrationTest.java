package com.mcmory.backend.consent;

import java.time.LocalDate;

import com.mcmory.backend.member.Member;
import com.mcmory.backend.member.MemberRepository;
import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동의 버전 관리임. 버전을 올려두고 다시 묻는 경로가 없으면 버전 컬럼은 장식이므로, 여기서 검증하는 것은 컬럼의 존재가 아니라 **버전이 어긋났을 때
 * 재동의를 요구하는가**다.
 *
 * 테스트마다 회원을 새로 만든다. 시드 회원을 쓰면 앞선 테스트가 남긴 동의 행 때문에 "이력 없음" 검증이 실행 순서에 의존한다 — 공용 컨테이너라 행이
 * 다음 실행까지 남는다(실측으로 깨졌다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsentVersionIntegrationTest extends HttpIntegrationSupport {

	private static final String PASSWORD = "abcd1234";

	@Autowired
	private ConsentRepository consents;

	@Autowired
	private MemberRepository members;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String phone;

	@BeforeEach
	void 동의_이력이_없는_새_회원으로_로그인() {
		clearCookies();

		// 가입 API로 만들면 동의 행이 함께 생겨 "이력 없음" 상태를 만들 수 없어 저장소로 직접 넣는다
		this.phone = "010" + String.format("%08d", Math.abs(System.nanoTime() % 100000000L));
		this.members.saveAndFlush(new Member("동의테스트", this.phone, this.passwordEncoder.encode(PASSWORD),
				LocalDate.of(2000, 1, 1), "NONE", false));

		loginAs(this.phone, PASSWORD);
	}

	@Test
	void 동의_이력이_없는_회원은_재동의가_필요하다() {
		var status = get("/api/v1/consents");

		assertThat(status.status()).isEqualTo(200);
		assertThat(status.body().get("needsAction").asBoolean()).isTrue();

		var privacy = itemOf(status, ConsentType.PRIVACY.name());
		assertThat(privacy.get("required").asBoolean()).isTrue();
		assertThat(privacy.get("agreed").asBoolean()).isFalse();
		assertThat(privacy.get("currentVersion").asString()).isEqualTo(ConsentType.PRIVACY.currentVersion());
	}

	@Test
	void 동의하면_현재_버전으로_기록되고_재동의_요구가_사라진다() {
		assertThat(post("/api/v1/consents", "{\"type\":\"PRIVACY\",\"agreed\":true}").status()).isEqualTo(200);

		var status = get("/api/v1/consents");
		var privacy = itemOf(status, ConsentType.PRIVACY.name());

		assertThat(privacy.get("agreed").asBoolean()).isTrue();
		assertThat(privacy.get("agreedVersion").asString()).isEqualTo(ConsentType.PRIVACY.currentVersion());
		assertThat(privacy.get("needsAction").asBoolean()).isFalse();
	}

	@Test
	void 선택_항목은_거절해도_재동의를_요구하지_않는다() {
		assertThat(post("/api/v1/consents", "{\"type\":\"SMS\",\"agreed\":false}").status()).isEqualTo(200);

		var sms = itemOf(get("/api/v1/consents"), ConsentType.SMS.name());
		assertThat(sms.get("agreed").asBoolean()).isFalse();
		assertThat(sms.get("needsAction").asBoolean()).isFalse();
	}

	@Test
	void 재동의는_기존_행을_고치지_않고_새_행을_남긴다() {
		long before = this.consents.count();

		post("/api/v1/consents", "{\"type\":\"SMS\",\"agreed\":true}");
		post("/api/v1/consents", "{\"type\":\"SMS\",\"agreed\":false}");

		// append-only라 두 번의 결정이 둘 다 남아야 함. 마지막 값만 남기면 언제 무엇에 동의했는지가 사라짐
		assertThat(this.consents.count()).isEqualTo(before + 2);
		assertThat(itemOf(get("/api/v1/consents"), ConsentType.SMS.name()).get("agreed").asBoolean()).isFalse();
	}

	@Test
	void SMS_동의를_바꾸면_회원의_캐시_컬럼도_따라간다() {
		post("/api/v1/consents", "{\"type\":\"SMS\",\"agreed\":true}");
		assertThat(this.members.findByPhoneAndDeletedAtIsNull(this.phone).orElseThrow().isSmsOptIn()).isTrue();

		post("/api/v1/consents", "{\"type\":\"SMS\",\"agreed\":false}");
		assertThat(this.members.findByPhoneAndDeletedAtIsNull(this.phone).orElseThrow().isSmsOptIn()).isFalse();
	}

	@Test
	void 필수_동의_철회는_거부된다() {
		var response = post("/api/v1/consents", "{\"type\":\"PRIVACY\",\"agreed\":false}");

		// 탈퇴가 MVP 밖이라 철회를 받아두면 "동의 없이 쓰는 회원"이 생김
		assertThat(response.status()).isEqualTo(400);
		assertThat(response.text()).contains("필수 동의는 철회할 수 없습니다");
	}

	@Test
	void 수신자_동의는_회원_항목이_아니다() {
		assertThat(post("/api/v1/consents", "{\"type\":\"RECIPIENT_PRIVACY\",\"agreed\":true}").status())
			.isEqualTo(400);
		assertThat(post("/api/v1/consents", "{\"type\":\"없는항목\",\"agreed\":true}").status()).isEqualTo(400);
	}

	private tools.jackson.databind.JsonNode itemOf(Response response, String type) {
		for (var item : response.body().get("items")) {
			if (type.equals(item.get("type").asString())) {
				return item;
			}
		}
		throw new AssertionError("항목을 찾지 못함: " + type);
	}

}
