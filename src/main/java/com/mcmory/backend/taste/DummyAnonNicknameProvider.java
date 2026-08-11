package com.mcmory.backend.taste;

import org.springframework.stereotype.Component;

/**
 * 시연용 더미임. 친구가 누구든 같은 값을 돌려줌.
 *
 * 고정값인 것이 의도임 — 무작위나 순번으로 흉내 내면 "이미 부여 규칙이 정해졌다"고 읽혀 미결 사항이 묻힘. 결정 셋은
 * {@link AnonNicknameProvider} 주석 참조.
 */
@Component
public class DummyAnonNicknameProvider implements AnonNicknameProvider {

	private static final String DUMMY = "멋쟁이사자";

	@Override
	public String senderNameFor(Long friendId) {
		return DUMMY;
	}

}
