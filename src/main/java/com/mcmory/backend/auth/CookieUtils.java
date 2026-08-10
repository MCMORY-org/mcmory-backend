package com.mcmory.backend.auth;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * ADR-013 결정 2와 5: 액세스와 리프레시를 모두 HttpOnly 쿠키로 전달하고, SameSite와 Secure는 설정으로 주입함.
 *
 * Secure를 무조건 true로 박으면 안 됨 — 웹뷰 로컬 검증은 http://10.0.2.2로 붙고 그 호스트는 secure context 취급을 받지
 * 못해 쿠키가 통째로 죽음. 배포 프로파일에서만 true로 올림.
 */
@Component
public class CookieUtils {

	public static final String ACCESS_COOKIE = "mcmory_at";

	public static final String REFRESH_COOKIE = "mcmory_rt";

	private final AuthProperties properties;

	public CookieUtils(AuthProperties properties) {
		this.properties = properties;
	}

	public String accessCookie(String value, Duration maxAge) {
		return build(ACCESS_COOKIE, value, maxAge);
	}

	public String refreshCookie(String value, Duration maxAge) {
		return build(REFRESH_COOKIE, value, maxAge);
	}

	public String expiredAccessCookie() {
		return build(ACCESS_COOKIE, "", Duration.ZERO);
	}

	public String expiredRefreshCookie() {
		return build(REFRESH_COOKIE, "", Duration.ZERO);
	}

	public String read(HttpServletRequest request, String name) {
		if (request.getCookies() == null) {
			return null;
		}
		for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
			if (name.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public HttpHeaders headersWith(String... setCookieValues) {
		HttpHeaders headers = new HttpHeaders();
		for (String value : setCookieValues) {
			headers.add(HttpHeaders.SET_COOKIE, value);
		}
		return headers;
	}

	private String build(String name, String value, Duration maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.path("/")
			.maxAge(maxAge)
			.secure(this.properties.getCookie().isSecure())
			.sameSite(this.properties.getCookie().getSameSite())
			.build()
			.toString();
	}

}
