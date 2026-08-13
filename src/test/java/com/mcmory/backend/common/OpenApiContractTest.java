package com.mcmory.backend.common;

import java.util.ArrayList;
import java.util.List;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 생성된 OpenAPI 문서가 실체와 어긋나지 않는지 봄. **컴파일이 통과해도 스펙은 틀릴 수 있어서** 이 테스트가 필요함 — 2026-08-13 감사에서
 * 스냅샷 재조회의 200 누락과 상태 변경 API의 403 누락을 컴파일은 하나도 잡지 못했음.
 *
 * `/v3/api-docs`는 인증 없이 열리므로 로그인하지 않음.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiContractTest extends HttpIntegrationSupport {

	private static final List<String> STATE_CHANGING = List.of("post", "put", "patch", "delete");

	private JsonNode paths() {
		var docs = get("/v3/api-docs");
		assertThat(docs.status()).as(docs.text()).isEqualTo(200);
		return docs.body().get("paths");
	}

	@Test
	void 모든_operation이_200이나_201을_선언한다() {
		List<String> missing = new ArrayList<>();
		JsonNode paths = paths();

		for (String path : paths.propertyNames()) {
			JsonNode item = paths.get(path);
			for (String method : item.propertyNames()) {
				JsonNode responses = item.get(method).get("responses");
				if (responses == null || (!responses.has("200") && !responses.has("201"))) {
					missing.add(method + " " + path);
				}
			}
		}

		assertThat(missing).as("성공 응답을 선언하지 않은 operation").isEmpty();
	}

	/**
	 * `OriginCheckFilter`가 상태 변경 메서드 전부를 검사하므로 문서도 전부 403을 가져야 함. GET에 붙으면 없는 실패를 있다고 말하는
	 * 것이라 함께 막음.
	 */
	@Test
	void 상태_변경_operation만_403을_가진다() {
		List<String> missing403 = new ArrayList<>();
		List<String> unexpected403 = new ArrayList<>();
		JsonNode paths = paths();

		for (String path : paths.propertyNames()) {
			JsonNode item = paths.get(path);
			for (String method : item.propertyNames()) {
				boolean has403 = item.get(method).get("responses").has("403");
				if (STATE_CHANGING.contains(method) && !has403) {
					missing403.add(method + " " + path);
				}
				if (!STATE_CHANGING.contains(method) && has403) {
					unexpected403.add(method + " " + path);
				}
			}
		}

		assertThat(missing403).as("403이 빠진 상태 변경 operation").isEmpty();
		assertThat(unexpected403).as("403이 붙으면 안 되는 조회 operation").isEmpty();
	}

	@Test
	void 공통_403_응답이_한_곳에만_정의된다() {
		var docs = get("/v3/api-docs");
		JsonNode responses = docs.body().get("components").get("responses");

		assertThat(responses.has("CommonForbiddenOrigin")).isTrue();
		assertThat(responses.get("CommonForbiddenOrigin").toString()).contains("COMMON403");
	}

}
