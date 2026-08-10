package com.mcmory.backend.consent;

import java.util.Arrays;
import java.util.List;

/**
 * 동의 항목과 그 현재 버전임. ADR-002 개정(2026-08-08)으로 필수는 개인정보 수집과 이용 1종이고 SMS는 선택임.
 *
 * 만 14세 확인을 항목에서 뺀 이유는 가입이 생년월일을 필수로 받아 서버가 이미 거부하기 때문임(SignupPolicy) — 자기 신고보다 생년월일 검증이
 * 강한 근거라 체크박스는 중복임.
 *
 * **버전을 여기 상수로 두는 이유**: 약관 본문 파일이 아직 없어 해시할 대상이 없고 버전이 하나뿐임. 별도 테이블로 두면 초기화 코드만 늘어남. 본문이
 * 생기고 버전이 둘 이상 공존해야 하면 그때 테이블로 올림 — 그때까지의 계약은 "동의 행에 그 시점 버전을 박는다"임.
 *
 * **버전을 올리면** 그 항목에 대한 기존 동의는 자동으로 재동의 필요 상태가 됨(ConsentService.status). 문안이 바뀌었는데 버전을 안
 * 올리면 아무도 다시 묻지 않으니, 문안 수정과 버전 인상은 같은 커밋에서 한다.
 */
public enum ConsentType {

	/** 개인정보 수집과 이용. 가입 필수임. */
	PRIVACY("개인정보 수집과 이용", "v1", true),

	/** SMS 수신. 선택이며 동의하지 않아도 가입됨 — 화면 라벨이 이미 "SMS 수신 여부(선택)"임. */
	SMS("SMS 수신", "v1", false),

	/**
	 * 수신자의 개인정보 열람 동의임. 회원 가입과 무관하게 초대 링크 최초 진입에서 받음(ADR-002 결정 6). 필수 목록에 넣지 않는 이유는 이것이
	 * 회원의 동의가 아니라 선물 건에 붙는 동의이기 때문임(consent 테이블의 gift_id 쪽).
	 */
	RECIPIENT_PRIVACY("수신자 개인정보 열람", "v1", false);

	private final String label;

	private final String currentVersion;

	private final boolean requiredForSignup;

	ConsentType(String label, String currentVersion, boolean requiredForSignup) {
		this.label = label;
		this.currentVersion = currentVersion;
		this.requiredForSignup = requiredForSignup;
	}

	public String label() {
		return this.label;
	}

	public String currentVersion() {
		return this.currentVersion;
	}

	public boolean isRequiredForSignup() {
		return this.requiredForSignup;
	}

	/** 가입에 필요한 필수 항목 전부임. 지금은 PRIVACY 하나임. */
	public static List<ConsentType> requiredForSignup() {
		return Arrays.stream(values()).filter(ConsentType::isRequiredForSignup).toList();
	}

	/** 회원이 마이페이지에서 관리하는 항목임. 수신자 동의는 회원의 것이 아니라 제외함. */
	public static List<ConsentType> memberScoped() {
		return List.of(PRIVACY, SMS);
	}

}
