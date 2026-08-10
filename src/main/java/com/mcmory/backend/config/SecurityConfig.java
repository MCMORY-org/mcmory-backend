package com.mcmory.backend.config;

import com.mcmory.backend.auth.JwtAuthenticationFilter;
import com.mcmory.backend.auth.OriginCheckFilter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ADR-013 결정 3: STATELESS 세션에 JWT 검증 필터를 씀.
 *
 * 인가는 여기서 경로별로 걸지 않고 각 API가 판정함. 이유 둘 — `/api/v1/auth/me`는 미인증에도 200에
 * `{"member":null}`이어야 하고, 오류 응답이 CustomResponse 봉투로 고정돼 있어 시큐리티 기본 401 본문과 섞이면 계약이 깨짐.
 *
 * 직접 SecurityFilterChain을 정의하는 순간 actuator health의 기본 면제가 사라지므로 permitAll에 함께 넣음. 배포
 * 헬스체크가 이 경로를 씀.
 *
 * **CSRF를 끄면 Origin 검사를 반드시 함께 켤 것.** 액세스 토큰이 쿠키라 브라우저가 자동으로 실어 보내므로, 둘 다 없으면 외부 사이트가 로그인
 * 사용자의 상태 변경 요청을 그대로 만들 수 있음.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
			OriginCheckFilter originFilter) throws Exception {
		return http.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
			.addFilterBefore(originFilter, UsernamePasswordAuthenticationFilter.class)
			.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
