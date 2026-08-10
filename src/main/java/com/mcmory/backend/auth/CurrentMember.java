package com.mcmory.backend.auth;

import com.mcmory.backend.global.apiPayload.code.AuthErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 로그인한 회원 id를 꺼내는 지점임. 인가를 시큐리티 경로 규칙이 아니라 API마다 두기로 했으므로(SecurityConfig 주석) 401 문구도 여기서
 * 하나로 고정함 — 프로토타입 라우트가 전부 같은 문구였고 스모크가 그 문구를 봄.
 */
@Component
public class CurrentMember {

	public Long requireId() {
		Long id = findId();
		if (id == null) {
			throw new CustomException(AuthErrorCode.LOGIN_REQUIRED);
		}
		return id;
	}

	public Long findId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Long memberId)) {
			return null;
		}
		return memberId;
	}

}
