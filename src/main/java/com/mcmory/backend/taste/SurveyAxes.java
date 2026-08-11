package com.mcmory.backend.taste;

import java.util.List;

import com.mcmory.backend.global.apiPayload.code.FriendErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;

/**
 * FEAT-W003 `HOME-02` 질문 선별임. 발송자가 켠 축만 `Start-02` 설문에 나감.
 *
 * **발송자가 취향을 답하는 것이 아니라 무엇을 물어볼지 켜고 끄는 것임** — 그래서 이 값은 추천 점수에 직접 들어가지 않고, 어떤 답을 받을 수 있는지만
 * 정함.
 *
 * 저장 형태는 `friend.survey_axes`의 `colors,styles,bags` 조인이고 **NULL은 세 축 전부임**. 컬럼을 축마다 세 개
 * 두지 않은 이유는 축이 늘면 스키마가 따라 늘고 배포 DB `ALTER`가 매번 붙기 때문임.
 */
public final class SurveyAxes {

	public static final String COLORS = "colors";

	public static final String STYLES = "styles";

	public static final String BAGS = "bags";

	/** 저장과 응답의 축 순서임. 화면 카드 순서와 같음. */
	private static final List<String> ALL = List.of(COLORS, STYLES, BAGS);

	private SurveyAxes() {
	}

	/** 저장값을 축 목록으로 폄. NULL과 빈 값은 세 축 전부임. */
	public static List<String> parse(String stored) {
		if (stored == null || stored.isBlank()) {
			return ALL;
		}
		List<String> picked = List.of(stored.split(","));
		return ALL.stream().filter(picked::contains).toList();
	}

	/**
	 * 저장 형태로 정규화함. 비어 있으면 NULL(세 축 전부)임.
	 *
	 * `colors`와 `styles`를 둘 다 끄면 막음 — 가방은 점수에 안 쓰여서 답을 받아도 추천이 그대로임(설문 제출의 같은 규칙과 짝).
	 */
	public static String format(List<String> raw) {
		if (raw == null || raw.isEmpty()) {
			return null;
		}
		for (String axis : raw) {
			if (!ALL.contains(axis)) {
				throw new CustomException(FriendErrorCode.INVALID_ANSWER);
			}
		}
		if (!raw.contains(COLORS) && !raw.contains(STYLES)) {
			throw new CustomException(FriendErrorCode.INVALID_ANSWER);
		}
		return String.join(",", ALL.stream().filter(raw::contains).toList());
	}

}
