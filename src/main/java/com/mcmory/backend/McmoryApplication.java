package com.mcmory.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * MCMORY 백엔드 진입점.
 *
 * 현재 이 저장소는 CI 골격만 있는 스캐폴드임. 도메인 코드는 `mcmory-proto-backend`에서 이슈 단위로 이식함(PROTO-W001 4단계).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class McmoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(McmoryApplication.class, args);
	}

}
