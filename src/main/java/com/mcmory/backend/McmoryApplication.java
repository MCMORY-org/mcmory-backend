package com.mcmory.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * MCMORY 백엔드 진입점.
 *
 * 모든 응답은 CustomResponse 봉투로 나가고 계약 정본은 API명세서임. 실패는 문구가 아니라 code로 분기할 것.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class McmoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(McmoryApplication.class, args);
	}

}
