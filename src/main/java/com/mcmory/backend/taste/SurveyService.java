package com.mcmory.backend.taste;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mcmory.backend.friend.Friend;
import com.mcmory.backend.friend.FriendRepository;
import com.mcmory.backend.global.apiPayload.code.FriendErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * FEAT-W001 `Start-02` 설문임. 수신자 답변을 `INVITE_ANSWER`로 남기고 그 값이 추천 점수에 들어감.
 *
 * 설문 토큰은 인증 주체가 아니라 리소스 식별자라 필터가 아니라 여기서 조회로 검증함(ADR-013 결정 4).
 */
@Service
public class SurveyService {

	/**
	 * v1.1 `HOME-02` 실측 선택지임. 추천 매칭 키이자 근거 문구라 허용 목록으로만 받음 — 자유 문자열은 매칭 0건인 값이 근거로 찍힘.
	 *
	 * **취향 색상 6종은 편지지 색상 4종, 상품 색상과 다른 축임** — 섞지 말 것.
	 */
	private static final Set<String> COLORS = Set.of("코냑", "블랙", "베이지", "핑크", "골드", "그레이");

	private static final Set<String> STYLES = Set.of("캐주얼", "미니멀", "스트릿", "클래식", "러블리", "포멀");

	private static final Set<String> BAGS = Set.of("숄더백", "토트백", "크로스바디", "백팩");

	private final FriendRepository friends;

	private final AnonNicknameProvider anonNicknames;

	private final TasteProfileRepository tasteProfiles;

	private final ObjectMapper objectMapper;

	public SurveyService(FriendRepository friends, AnonNicknameProvider anonNicknames,
			TasteProfileRepository tasteProfiles, ObjectMapper objectMapper) {
		this.friends = friends;
		this.anonNicknames = anonNicknames;
		this.tasteProfiles = tasteProfiles;
		this.objectMapper = objectMapper;
	}

	/** `Start-01` 표시용임. 동의 전 화면이라 누가 누구에게 물었는지만 줌. */
	public record SurveyView(String senderName, String friendName, boolean answered) {
	}

	@Transactional(readOnly = true)
	public SurveyView read(String surveyToken) {
		Friend friend = requireFriend(surveyToken, false);

		// 발송자 실명을 토큰 소지자에게 주지 않음. 표시명 규칙이 미결이라 지금은 더미임(AnonNicknameProvider 주석)
		String senderName = this.anonNicknames.senderNameFor(friend.getId());

		boolean answered = this.tasteProfiles.findByFriendId(friend.getId())
			.map(TasteProfile::isFromInvite)
			.orElse(false);

		return new SurveyView(senderName, friend.displayName(), answered);
	}

	/**
	 * `Start-02` 제출임. 재제출은 덮어씀(ADR-009 결정 1의 친구당 1행).
	 *
	 * 동의는 값만 검증하고 이력 행을 남기지 않음 — `consent`의 CHECK가 member_id나 gift_id를 요구하는데 설문 시점에는 선물이
	 * 없음.
	 */
	@Transactional
	public void submit(String surveyToken, boolean privacyAgreed, List<String> colors, List<String> styles,
			List<String> bags) {
		// 친구 행을 잠가 동시 제출과 친구 삭제와 발송자 저장을 직렬화함. 잠금 없이는 유니크 위반이 COMMON500으로 샘
		Friend friend = requireFriend(surveyToken, true);

		if (!privacyAgreed) {
			throw new CustomException(FriendErrorCode.PRIVACY_REQUIRED);
		}

		List<String> pickedColors = validated(colors, COLORS);
		List<String> pickedStyles = validated(styles, STYLES);
		List<String> pickedBags = validated(bags, BAGS);

		// 점수에 쓰는 두 축 중 하나는 있어야 함. 가방만 고르면 답변은 있는데 추천이 그대로임
		if (pickedColors.isEmpty() && pickedStyles.isEmpty()) {
			throw new CustomException(FriendErrorCode.INVALID_ANSWER);
		}

		// flush를 끼우지 않으면 Hibernate가 INSERT를 DELETE보다 먼저 내보내 uq_taste_friend를 위반함
		this.tasteProfiles.deleteByFriendId(friend.getId());
		this.tasteProfiles.flush();
		this.tasteProfiles
			.save(TasteProfile.forInvite(friend.getId(), toAnswersJson(pickedColors, pickedStyles, pickedBags)));
	}

	private Friend requireFriend(String surveyToken, boolean forUpdate) {
		if (surveyToken == null || surveyToken.isBlank()) {
			throw new CustomException(FriendErrorCode.NOT_FOUND);
		}
		Optional<Friend> found = forUpdate ? this.friends.findBySurveyTokenForUpdate(surveyToken)
				: this.friends.findBySurveyTokenAndDeletedAtIsNull(surveyToken);
		return found.orElseThrow(() -> new CustomException(FriendErrorCode.NOT_FOUND));
	}

	/** 선택지 밖 값은 버리지 않고 막음 — 버리면 고른 것이 사라진 채 200이 나감. */
	private List<String> validated(List<String> raw, Set<String> allowed) {
		if (raw == null) {
			return List.of();
		}
		// null 원소를 trim에 넘기면 사용자 입력 오류가 NPE로 바뀌어 COMMON500이 됨
		List<String> picked = raw.stream().map((value) -> (value == null) ? "" : value.trim()).distinct().toList();
		for (String value : picked) {
			if (!allowed.contains(value)) {
				throw new CustomException(FriendErrorCode.INVALID_ANSWER);
			}
		}
		return picked;
	}

	/**
	 * 복수 키로 저장함. 단수 키(`color`·`style`)는 시드 형식이고 추천이 둘 다 읽음.
	 *
	 * `bags`는 저장만 함 — 시드 상품에 가방 디자인 축이 없어 매칭할 대상이 없음.
	 */
	private String toAnswersJson(List<String> colors, List<String> styles, List<String> bags) {
		try {
			Map<String, Object> answers = new LinkedHashMap<>();
			answers.put("colors", colors);
			answers.put("styles", styles);
			answers.put("bags", bags);
			return this.objectMapper.writeValueAsString(answers);
		}
		catch (Exception ex) {
			throw new IllegalStateException("설문 답변 직렬화 실패", ex);
		}
	}

}
