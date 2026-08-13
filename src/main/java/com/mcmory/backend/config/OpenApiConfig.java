package com.mcmory.backend.config;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;

import org.springdoc.core.customizers.OpenApiCustomizer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 문서의 제목과 서버 목록임. 선언하지 않으면 springdoc이 요청에서 서버 URL을 만드는데, **nginx 뒤라 그 값이
 * `http://`로 나가 `Try it out`이 전부 막힘.**
 *
 * `server.forward-headers-strategy`로 고치지 않는 이유: 켜면 `X-Forwarded-Host` 위조로
 * `OriginCheckFilter`(getServerName 사용)를 우회할 수 있음(OPS-W003 결정).
 */
@Configuration
public class OpenApiConfig {

	/** 공통 403 응답의 참조 이름임. 상태 변경 operation이 전부 이 하나를 가리킴. */
	static final String FORBIDDEN_ORIGIN_RESPONSE = "CommonForbiddenOrigin";

	private static final String COMMON403_EXAMPLE = """
			{
			  "isSuccess": false,
			  "code": "COMMON403",
			  "message": "현재 접속한 환경에서는 요청을 처리할 수 없습니다. 공식 앱이나 웹에서 다시 시도해주세요",
			  "result": null
			}""";

	/**
	 * 배포를 먼저 두어 기본 선택이 되게 함. 로컬은 드롭다운에서 두 번째를 고르면 됨 — 환경변수를 쓰지 않는 이유는 배포 `.env`가 단일 시크릿이라
	 * 키 하나 추가에 전체를 덮어써야 하기 때문임.
	 */
	@Bean
	OpenAPI mcmoryOpenApi() {
		return new OpenAPI()
			.info(new Info().title("MCMORY API")
				.version("v1")
				.description("계약 정본은 docs/architecture/API명세서.md임. 모든 응답은 CustomResponse 봉투로 나가고 "
						+ "실패는 문구가 아니라 code로 분기할 것."))
			.servers(List.of(new Server().url("https://api.cartlab.store").description("배포"),
					new Server().url("http://localhost:8080").description("로컬")))
			.components(new Components().addResponses(FORBIDDEN_ORIGIN_RESPONSE, forbiddenOrigin()));
	}

	/**
	 * `OriginCheckFilter`가 POST·PUT·PATCH·DELETE **전부**를 경로 구분 없이 검사하므로, 403도 컨트롤러마다 적지
	 * 않고 여기서 한 번에 붙임. 개별로 적으면 엔드포인트가 늘 때마다 빠뜨려 문서가 실체와 갈라짐.
	 *
	 * GET에는 붙이지 않음 — 필터가 상태 변경 메서드만 보기 때문임. 이미 403을 선언한 operation은 건드리지 않음.
	 */
	@Bean
	OpenApiCustomizer forbiddenOriginOnStateChanging() {
		return openApi -> openApi.getPaths().values().forEach(path -> {
			mark(path.getPost());
			mark(path.getPut());
			mark(path.getPatch());
			mark(path.getDelete());
		});
	}

	private static void mark(Operation operation) {
		if (operation == null || operation.getResponses() == null || operation.getResponses().containsKey("403")) {
			return;
		}
		operation.getResponses()
			.addApiResponse("403", new ApiResponse().$ref("#/components/responses/" + FORBIDDEN_ORIGIN_RESPONSE));
	}

	private static ApiResponse forbiddenOrigin() {
		return new ApiResponse()
			.description("`COMMON403` — 허용 목록 밖 Origin에서 온 요청임. 사용자 권한 문제가 아니라 호출한 곳의 문제이므로 "
					+ "배포 도메인이 `app.auth.allowed-origins`에 있는지부터 볼 것. `Origin` 헤더가 없는 요청은 검사를 통과함.")
			.content(new Content().addMediaType("application/json",
					new MediaType().examples(Map.of("COMMON403", new Example().value(COMMON403_EXAMPLE)))));
	}

}
