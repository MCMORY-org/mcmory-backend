package com.mcmory.backend.config;

import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

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
					new Server().url("http://localhost:8080").description("로컬")));
	}

}
