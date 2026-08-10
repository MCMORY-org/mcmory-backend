package com.mcmory.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

/**
 * 액세스 JWT 발급과 검증, 리프레시 토큰 생성과 해싱을 담당함.
 *
 * 리프레시는 원문을 저장하지 않고 SHA-256 해시만 저장함(테이블 컬럼 이름이 token_hash인 이유). 원문은 쿠키에만 있음.
 */
// final인 이유: 생성자가 키 길이 미달을 즉시 예외로 올림(설정 실수를 기동 시점에 잡는 편이 낫다). 상속 가능하면
// 부분 초기화된 객체가 finalizer로 노출될 수 있어 SpotBugs CT_CONSTRUCTOR_THROW가 걸림 — 예외를 없애는 대신 상속을 막음
@Component
public final class Tokens {

	private final SecretKey key;

	private final Duration accessTtl;

	private final Duration refreshTtl;

	private final SecureRandom random = new SecureRandom();

	public Tokens(AuthProperties properties) {
		this.key = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
		this.accessTtl = properties.getAccessTtl();
		this.refreshTtl = properties.getRefreshTtl();
	}

	public String issueAccessToken(Long memberId) {
		long now = System.currentTimeMillis();
		return Jwts.builder()
			.subject(String.valueOf(memberId))
			.issuedAt(new Date(now))
			.expiration(new Date(now + this.accessTtl.toMillis()))
			.signWith(this.key)
			.compact();
	}

	/** 서명과 만료가 유효하면 memberId, 아니면 null임. 만료를 예외로 올리지 않는 이유는 필터가 미인증으로만 다루면 되기 때문임. */
	public Long readMemberId(String accessToken) {
		try {
			String subject = Jwts.parser()
				.verifyWith(this.key)
				.build()
				.parseSignedClaims(accessToken)
				.getPayload()
				.getSubject();
			return Long.valueOf(subject);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	public String newRefreshToken() {
		byte[] buffer = new byte[32];
		this.random.nextBytes(buffer);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
	}

	public String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256을 사용할 수 없음", ex);
		}
	}

	public Duration getRefreshTtl() {
		return this.refreshTtl;
	}

}
