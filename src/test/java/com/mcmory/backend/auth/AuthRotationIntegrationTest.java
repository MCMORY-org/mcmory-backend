package com.mcmory.backend.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mcmory.backend.consent.Consent;
import com.mcmory.backend.consent.ConsentRepository;
import com.mcmory.backend.consent.ConsentType;
import com.mcmory.backend.member.Member;
import com.mcmory.backend.member.MemberRepository;
import com.mcmory.backend.support.MySqlContainerSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리프레시 토큰 회전의 경쟁 상태 검증 4항임. 유예창이 없으면 동시에 두 번 재발급할 때 진 쪽이 401을 받아 사용자가 튕긴다.
 *
 * **인메모리 DB로는 재현되지 않음** — 유예 판정은 진 요청이 이긴 요청이 방금 커밋한 prev 해시를 읽어야 성립하고, 그 가시성이 MySQL
 * REPEATABLE READ 스냅샷과 트랜잭션 경계의 상호작용이라 실제 MySQL이 필요함.
 *
 * HTTP 블랙박스로 도는 이유는 검증 대상 하나가 **Set-Cookie 헤더의 부재**라서임. 서비스 반환값만 보면 그것을 못 봄.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthRotationIntegrationTest extends MySqlContainerSupport {

	private static final long DEFAULT_GRACE_SECONDS = 30;

	@LocalServerPort
	private int port;

	@Autowired
	private AuthProperties properties;

	@Autowired
	private MemberRepository members;

	@Autowired
	private ConsentRepository consents;

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@AfterEach
	void restoreGrace() {
		// 프로퍼티를 런타임에 되돌려 컨텍스트를 쪼개지 않음. @TestPropertySource로 갈랐다면 캐시 키가 분열해
		// 컨테이너 재사용 이득이 사라짐(TEST-W001 3장 원칙)
		this.properties.getRefresh().setGraceSeconds(DEFAULT_GRACE_SECONDS);
	}

	@Test
	void 같은_쿠키로_동시_두_발이_둘_다_200이고_회전은_정확히_한_번() throws Exception {
		String refreshToken = login();

		CompletableFuture<HttpResponse<String>> first = reissueAsync(refreshToken);
		CompletableFuture<HttpResponse<String>> second = reissueAsync(refreshToken);
		CompletableFuture.allOf(first, second).join();

		assertThat(first.get().statusCode()).isEqualTo(200);
		assertThat(second.get().statusCode()).isEqualTo(200);

		long rotatedCount = List.of(first.get(), second.get())
			.stream()
			.filter((response) -> hasRefreshCookie(response))
			.count();
		assertThat(rotatedCount).isEqualTo(1);
	}

	@Test
	void 개인정보_미동의_가입은_400이고_회원이_생기지_않음() throws Exception {
		// TC-001. ADR-002 개정으로 필수는 개인정보 1종임
		String phone = newPhone();
		HttpResponse<String> response = signup(phone, signupBody(phone, false, false));

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).contains("개인정보 수집과 이용에 동의해주세요");
		assertThat(this.members.findByPhoneAndDeletedAtIsNull(phone)).isEmpty();
	}

	@Test
	void SMS_미동의는_가입을_막지_않음() throws Exception {
		// 선택 항목이라 거절해도 가입은 됨. 다만 "거절함" 이력은 남아야 함
		String phone = newPhone();
		assertThat(signup(phone, signupBody(phone, true, false)).statusCode()).isEqualTo(200);

		Member member = this.members.findByPhoneAndDeletedAtIsNull(phone).orElseThrow();
		assertThat(this.consents.findByMemberIdOrderById(member.getId())).anySatisfy((consent) -> {
			assertThat(consent.getConsentType()).isEqualTo(ConsentType.SMS.name());
			assertThat(consent.isAgreed()).isFalse();
		});
	}

	@Test
	void 정상_가입은_200이고_쿠키_2종과_동의_이력이_버전과_함께_저장됨() throws Exception {
		String phone = newPhone();
		HttpResponse<String> response = signup(phone, signupBody(phone, true, false));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(hasRefreshCookie(response)).isTrue();
		assertThat(response.headers()
			.allValues("set-cookie")
			.stream()
			.anyMatch((value) -> value.startsWith(CookieUtils.ACCESS_COOKIE + "="))).isTrue();

		Member member = this.members.findByPhoneAndDeletedAtIsNull(phone).orElseThrow();
		List<Consent> consents = this.consents.findByMemberIdOrderById(member.getId());

		assertThat(consents.stream().map(Consent::getConsentType)).containsExactlyInAnyOrder(ConsentType.PRIVACY.name(),
				ConsentType.SMS.name());
		// 동의 시점의 버전이 행에 박혀야 함. 이 값이 없으면 나중에 재동의 필요를 판정할 수 없음
		assertThat(consents).allSatisfy((consent) -> assertThat(consent.getTermsVersion()).isNotBlank());
		assertThat(member.isSmsOptIn()).isFalse();
	}

	@Test
	void 가입한_회원으로_로그인이_됨() throws Exception {
		String phone = newPhone();
		assertThat(signup(phone, signupBody(phone, true, false)).statusCode()).isEqualTo(200);

		HttpResponse<String> login = this.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("{\"phone\":\"" + phone + "\",\"password\":\"abcd1234\"}"))
			.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(login.body()).contains("신규회원");
	}

	@Test
	void 중복_전화번호_가입은_409() throws Exception {
		// 시드 회원의 번호로 가입 시도
		HttpResponse<String> response = signup("01012345678", signupBody("01012345678", true, false));

		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(response.body()).contains("이미 가입된 전화번호입니다");
	}

	@Test
	void 모르는_토큰은_401() throws Exception {
		// 쿠키 값은 ASCII로 둠 — HttpClient가 비ASCII 헤더 값을 거부해 서버가 아니라 클라이언트에서 먼저 깨짐
		assertThat(reissue("not-a-real-refresh-token").statusCode()).isEqualTo(401);
	}

	@Test
	void 유예창_0초에서_재사용은_401() throws Exception {
		String refreshToken = login();
		assertThat(reissue(refreshToken).statusCode()).isEqualTo(200);

		this.properties.getRefresh().setGraceSeconds(0);

		assertThat(reissue(refreshToken).statusCode()).isEqualTo(401);
	}

	@Test
	void 유예_경로는_Set_Cookie를_보내지_않음() throws Exception {
		String refreshToken = login();
		HttpResponse<String> rotated = reissue(refreshToken);
		assertThat(hasRefreshCookie(rotated)).isTrue();

		// 같은(이미 교체된) 토큰으로 다시 요청함. 유예창 안이므로 200이지만 이긴 요청이 심은 쿠키를 덮지 않아야 함
		HttpResponse<String> grace = reissue(refreshToken);

		assertThat(grace.statusCode()).isEqualTo(200);
		assertThat(hasRefreshCookie(grace)).isFalse();
		assertThat(grace.body()).contains("\"rotated\":false");
	}

	/** 전화번호는 UNIQUE라 테스트마다 새 값을 씀. 회원 삭제 API가 없어 정리 대신 격리로 감. */
	private String newPhone() {
		return "010" + String.format("%08d", Math.abs(System.nanoTime() % 100000000L));
	}

	/** 형식 문자열에 줄바꿈을 넣지 않음 — SpotBugs VA_FORMAT_STRING_USES_NEWLINE이 걸리고, 여기선 한 줄로 충분함. */
	private String signupBody(String phone, boolean privacyAgreed, boolean smsOptIn) {
		return ("{\"name\":\"신규회원\",\"phone\":\"%s\",\"password\":\"abcd1234\",\"birthDate\":\"2000-01-01\","
				+ "\"gender\":\"NONE\",\"privacyAgreed\":%s,\"smsOptIn\":%s}")
			.formatted(phone, privacyAgreed, smsOptIn);
	}

	private HttpResponse<String> signup(String phone, String body) throws Exception {
		return this.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/signup"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private String login() throws Exception {
		HttpResponse<String> response = this.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("{\"phone\":\"01012345678\",\"password\":\"1234\"}"))
			.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		return refreshCookieValue(response);
	}

	private HttpResponse<String> reissue(String refreshToken) throws Exception {
		return this.client.send(reissueRequest(refreshToken), HttpResponse.BodyHandlers.ofString());
	}

	private CompletableFuture<HttpResponse<String>> reissueAsync(String refreshToken) {
		return this.client.sendAsync(reissueRequest(refreshToken), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest reissueRequest(String refreshToken) {
		return HttpRequest.newBuilder(uri("/api/v1/auth/reissue"))
			.header("Cookie", CookieUtils.REFRESH_COOKIE + "=" + refreshToken)
			.POST(HttpRequest.BodyPublishers.noBody())
			.build();
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.port + path);
	}

	private boolean hasRefreshCookie(HttpResponse<String> response) {
		return response.headers()
			.allValues("set-cookie")
			.stream()
			.anyMatch((value) -> value.startsWith(CookieUtils.REFRESH_COOKIE + "="));
	}

	private String refreshCookieValue(HttpResponse<String> response) {
		return response.headers()
			.allValues("set-cookie")
			.stream()
			.filter((value) -> value.startsWith(CookieUtils.REFRESH_COOKIE + "="))
			.map((value) -> value.substring((CookieUtils.REFRESH_COOKIE + "=").length()).split(";")[0])
			.findFirst()
			.orElseThrow(() -> new AssertionError("로그인 응답에 리프레시 쿠키가 없음"));
	}

}
