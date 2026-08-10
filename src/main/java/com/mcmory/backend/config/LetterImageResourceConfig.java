package com.mcmory.backend.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * FR-012 편지 사진 서빙임. 업로드 디렉터리를 `/letter-images/**`로 그대로 내보냄.
 *
 * `/api/` 아래에 두지 않은 이유는 이 경로만 봉투가 아니라 바이너리를 반환하기 때문임 — API 계약(모든 응답이 CustomResponse)과 섞이면
 * 프론트가 응답 형태를 갈라 읽어야 함.
 */
@Configuration
public class LetterImageResourceConfig implements WebMvcConfigurer {

	private final Path directory;

	public LetterImageResourceConfig(@Value("${app.letter-images.dir}") String directory) {
		this.directory = Path.of(directory).toAbsolutePath();
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/letter-images/**").addResourceLocations(this.directory.toUri().toString());
	}

}
