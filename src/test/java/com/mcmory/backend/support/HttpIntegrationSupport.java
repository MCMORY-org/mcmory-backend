package com.mcmory.backend.support;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.test.web.server.LocalServerPort;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 블랙박스 통합 테스트의 공용 기반임. 쿠키를 직접 들고 다니는 이유는 검증 대상 일부가 **Set-Cookie 헤더 자체**이기 때문임 —
 * CookieHandler에 맡기면 헤더가 왔는지 안 왔는지를 볼 수 없음.
 *
 * MySqlContainerSupport를 상속하므로 이 기반을 쓰는 클래스들은 컨테이너와 스프링 컨텍스트를 공유함. 하위 클래스가
 *
 * @TestPropertySource나 @MockitoBean을 붙이면 캐시 키가 갈라져 그 이득이 사라짐(TEST-W001 3장).
 */
public abstract class HttpIntegrationSupport extends MySqlContainerSupport {

	protected static final ObjectMapper JSON = new ObjectMapper();

	@LocalServerPort
	private int port;

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private final Map<String, String> cookies = new LinkedHashMap<>();

	/**
	 * body()는 **봉투를 벗긴 result**다. 테스트가 매번 `.get("result")`를 붙이지 않게 하려는 것이고, 봉투
	 * 자체(isSuccess, code)를 검증할 때는 envelope()을 쓴다.
	 *
	 * 실패 응답은 result가 없으므로 `{error, code}` 형태로 바꿔 준다 — 문구 단언을 봉투 도입 전과 같게 유지한다.
	 */
	protected record Response(int status, JsonNode body, JsonNode envelope, List<String> setCookies) {

		public String text() {
			return this.envelope == null ? "" : this.envelope.toString();
		}

		public String code() {
			return (this.envelope == null || !this.envelope.has("code")) ? null : this.envelope.get("code").asString();
		}

	}

	protected void clearCookies() {
		this.cookies.clear();
	}

	protected Response get(String path) {
		return send(HttpRequest.newBuilder(uri(path)).GET());
	}

	/** Origin 헤더를 실은 POST임. 필터 계층의 응답 계약을 보려면 브라우저처럼 이 헤더를 붙여야 함. */
	protected Response postFromOrigin(String path, String jsonBody, String origin) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "application/json")
			.header("Origin", origin);
		return send(builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)));
	}

	/**
	 * multipart 업로드임. 파트 이름은 컨트롤러 계약대로 `files` 고정임.
	 *
	 * **CRLF를 정확히 지켜야 한다** — LF만 쓰면 톰캣 파서가 파트를 자르지 못하고 요청이 통째로 깨짐. 경계는 UUID로 만들어 본문과 충돌하지
	 * 않게 함. 파일명은 서버가 쓰지 않으므로 아무 값이나 됨(확장자는 선언 MIME으로 정함).
	 */
	protected Response postFiles(String path, String contentType, List<byte[]> files) {
		String boundary = "mcmory" + UUID.randomUUID().toString().replace("-", "");
		ByteArrayOutputStream body = new ByteArrayOutputStream();

		int index = 0;
		for (byte[] file : files) {
			String header = "--" + boundary + "\r\n" + "Content-Disposition: form-data; name=\"files\"; filename=\"p"
					+ (index++) + "\"\r\n" + "Content-Type: " + contentType + "\r\n\r\n";
			body.writeBytes(header.getBytes(StandardCharsets.UTF_8));
			body.writeBytes(file);
			body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
		}
		body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

		HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "multipart/form-data; boundary=" + boundary);
		return send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
	}

	/** 파트가 아예 없는 multipart임. `files`가 필수일 때와 아닐 때의 응답이 갈리는 지점을 보기 위함임. */
	protected Response postEmptyMultipart(String path) {
		String boundary = "mcmory" + UUID.randomUUID().toString().replace("-", "");
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "multipart/form-data; boundary=" + boundary);
		return send(builder.POST(HttpRequest.BodyPublishers.ofString("--" + boundary + "--\r\n")));
	}

	/** 본문 있는 DELETE임. 친구 삭제가 id를 본문으로 받음. */
	protected Response delete(String path, String jsonBody) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
		return send(builder.method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody)));
	}

	protected Response patch(String path, String jsonBody) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
		return send(builder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody)));
	}

	protected Response post(String path, String jsonBody) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
		return send((jsonBody == null) ? builder.POST(HttpRequest.BodyPublishers.noBody())
				: builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)));
	}

	/** 로그인하고 쿠키를 이 인스턴스에 보관함. 이후 호출은 자동으로 그 쿠키를 실음. */
	protected void loginAs(String phone, String password) {
		Response response = post("/api/v1/auth/login",
				"{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}");
		assertThat(response.status()).isEqualTo(200);
	}

	private Response send(HttpRequest.Builder builder) {
		if (!this.cookies.isEmpty()) {
			builder.header("Cookie", String.join("; ",
					this.cookies.entrySet().stream().map((e) -> e.getKey() + "=" + e.getValue()).toList()));
		}

		try {
			HttpResponse<String> response = this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			List<String> setCookies = new ArrayList<>(response.headers().allValues("set-cookie"));
			storeCookies(setCookies);

			JsonNode envelope = response.body().isBlank() ? null : JSON.readTree(response.body());
			return new Response(response.statusCode(), unwrap(envelope), envelope, setCookies);
		}
		catch (Exception ex) {
			throw new IllegalStateException("요청 실패", ex);
		}
	}

	/** 봉투에서 result를 꺼냄. 실패면 문구와 코드를 담은 객체로 바꿈. 봉투가 아니면 그대로 돌려줌. */
	private JsonNode unwrap(JsonNode envelope) {
		if (envelope == null || !envelope.has("isSuccess")) {
			return envelope;
		}
		if (envelope.get("isSuccess").asBoolean()) {
			JsonNode result = envelope.get("result");
			return (result == null || result.isNull()) ? JSON.createObjectNode() : result;
		}
		return JSON.createObjectNode()
			.put("error", envelope.path("message").asString())
			.put("code", envelope.path("code").asString());
	}

	private void storeCookies(List<String> setCookies) {
		for (String header : setCookies) {
			String pair = header.split(";")[0];
			int equalsAt = pair.indexOf('=');
			if (equalsAt <= 0) {
				continue;
			}
			String name = pair.substring(0, equalsAt);
			String value = pair.substring(equalsAt + 1);
			// 빈 값은 만료 지시임(로그아웃). 보관하면 다음 요청이 죽은 쿠키를 실어 보냄
			if (value.isEmpty()) {
				this.cookies.remove(name);
			}
			else {
				this.cookies.put(name, value);
			}
		}
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.port + path);
	}

}
