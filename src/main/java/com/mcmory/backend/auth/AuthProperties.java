package com.mcmory.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ADR-013 결정 9의 1항: 유예창 기본값 30초임. campus가 문서에 5초로 잘못 적었다가 정정한 이력이 있어 **설정 키와 문서 표기를 반드시
 * 일치**시켜야 함. 문서 표기는 application.yml 주석과 ADR-013 결정 9의 1항임.
 */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

	private String jwtSecret = "mcmory-prototype-dev-secret-key-change-in-deploy";

	private Duration accessTtl = Duration.ofMinutes(30);

	private Duration refreshTtl = Duration.ofDays(1);

	private Cookie cookie = new Cookie();

	private Refresh refresh = new Refresh();

	public static class Cookie {

		private String sameSite = "Lax";

		private boolean secure;

		public String getSameSite() {
			return this.sameSite;
		}

		public void setSameSite(String sameSite) {
			this.sameSite = sameSite;
		}

		public boolean isSecure() {
			return this.secure;
		}

		public void setSecure(boolean secure) {
			this.secure = secure;
		}

	}

	public static class Refresh {

		private long graceSeconds = 30;

		public long getGraceSeconds() {
			return this.graceSeconds;
		}

		public void setGraceSeconds(long graceSeconds) {
			this.graceSeconds = graceSeconds;
		}

	}

	public String getJwtSecret() {
		return this.jwtSecret;
	}

	public void setJwtSecret(String jwtSecret) {
		this.jwtSecret = jwtSecret;
	}

	public Duration getAccessTtl() {
		return this.accessTtl;
	}

	public void setAccessTtl(Duration accessTtl) {
		this.accessTtl = accessTtl;
	}

	public Duration getRefreshTtl() {
		return this.refreshTtl;
	}

	public void setRefreshTtl(Duration refreshTtl) {
		this.refreshTtl = refreshTtl;
	}

	public Cookie getCookie() {
		return this.cookie;
	}

	public void setCookie(Cookie cookie) {
		this.cookie = cookie;
	}

	public Refresh getRefresh() {
		return this.refresh;
	}

	public void setRefresh(Refresh refresh) {
		this.refresh = refresh;
	}

}
