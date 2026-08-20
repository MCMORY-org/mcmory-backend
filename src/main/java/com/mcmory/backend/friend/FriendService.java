package com.mcmory.backend.friend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mcmory.backend.global.apiPayload.code.FriendErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.common.Phones;
import com.mcmory.backend.common.Tokens;
import com.mcmory.backend.taste.SurveyAxes;
import com.mcmory.backend.taste.TasteProfile;
import com.mcmory.backend.taste.TasteProfileRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {

	/** `friend.name` 컬럼 길이임. 선물 발송 경로도 같은 상한을 써야 함(그쪽은 create를 거치지 않음). */
	public static final int NAME_MAX = 20;

	private final FriendRepository friends;

	private final TasteProfileRepository tasteProfiles;

	private final ObjectMapper objectMapper;

	public FriendService(FriendRepository friends, TasteProfileRepository tasteProfiles, ObjectMapper objectMapper) {
		this.friends = friends;
		this.tasteProfiles = tasteProfiles;
		this.objectMapper = objectMapper;
	}

	/** 목록에는 취향 요약만 붙임. 원본 답변은 taste_profile에 있음(ADR-009 이원화). */
	@Schema(name = "FriendView", description = "친구 목록 항목임")
	public record FriendView(
			@Schema(description = "친구 id. 취향 저장·삭제·수정·설문 링크 발급 요청에 그대로 씀", example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED) Long id,
			@Schema(description = "친구 이름. 1자 이상 20자 이하임. **삭제된 친구는 목록에 나오지 않음** — "
					+ "삭제 시 이름과 전화번호를 DB에서 즉시 NULL로 파기함", example = "김민지",
					requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "친구 전화번호. 숫자만 저장하므로 하이픈 없이 나감(`010` 시작 10에서 11자리). "
					+ "선물 발송 경로가 이름으로 만든 친구 행은 번호를 나중에 채우므로 null일 수 있음", example = "01012345678",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) String phone,
			@Schema(description = "취향 요약 한 줄임. 취향이 아직 없으면 null임 — 발송자의 취향 저장이나 수신자 설문 제출로 채워짐. "
					+ "재료는 취향 색상 6종(코냑·블랙·베이지·핑크·골드·그레이)과 "
					+ "옷 스타일 6종(캐주얼·미니멀·스트릿·클래식·러블리·포멀)과 가방 4종(숄더백·토트백·크로스바디·백팩)임. "
					+ "**이 색상 축은 편지지 색 4종(GOLD·BLACK·BEIGE·PINK)과도 상품 색상과도 다른 축이라 섞지 말 것**", example = "블랙, 미니멀",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) String tasteSummary) {
	}

	@Transactional(readOnly = true)
	public List<FriendView> list(Long memberId) {
		List<Friend> rows = this.friends.findByOwnerMemberIdAndDeletedAtIsNullOrderById(memberId);
		Map<Long, String> summaries = summariesOf(rows);

		return rows.stream()
			.map((friend) -> new FriendView(friend.getId(), friend.getName(), friend.getPhone(),
					summaries.get(friend.getId())))
			.toList();
	}

	@Transactional
	public Friend create(Long memberId, String rawName, String rawPhone) {
		String name = (rawName == null) ? "" : rawName.trim();
		String phone = Phones.digits(rawPhone);

		// ADR-008 유효성. 문구는 디자인 v1.0의 인라인 에러 표기를 따름
		if (name.isEmpty() || name.length() > NAME_MAX) {
			throw new CustomException(FriendErrorCode.INVALID_NAME);
		}
		if (!Phones.isValid(phone)) {
			throw new CustomException(FriendErrorCode.INVALID_PHONE);
		}

		try {
			return this.friends.saveAndFlush(new Friend(memberId, name, phone));
		}
		catch (DataIntegrityViolationException ex) {
			// UNIQUE(owner_member_id, phone). 조회로 먼저 막지 않고 제약에 맡김 — 동시 등록에서도 한 건만 통과함
			throw new CustomException(FriendErrorCode.DUPLICATE_PHONE);
		}
	}

	@Transactional
	public void erase(Long memberId, Long friendId) {
		// 설문 제출과 같은 행 잠금을 씀 — 안 그러면 삭제 직후 도착한 제출이 파기된 친구에 취향을 다시 심음
		Friend friend = this.friends.findByIdAndOwnerForUpdate(friendId, memberId)
			.orElseThrow(() -> new CustomException(FriendErrorCode.NOT_FOUND));

		friend.erase();
		// ADR-003 결정 4: 취향도 함께 파기함
		this.tasteProfiles.deleteByFriendId(friend.getId());
	}

	/**
	 * 저장했는지를 돌려줌. **200에 false가 실릴 수 있음** — 덮지 않은 것을 성공으로만 알리면 화면이 저장됐다고 오인함.
	 *
	 * 409로 하지 않은 이유: 같은 값을 다시 보내도 안전해야 하고(멱등), 실패가 아니라 "이미 더 신뢰할 값이 있음"이기 때문임.
	 */
	@Transactional
	public boolean saveTaste(Long memberId, Long friendId, String summary) {
		// 설문 제출과 같은 행 잠금을 씀 — 동시에 들어오면 둘 다 DELETE를 통과해 uq_taste_friend가 터짐
		Friend friend = this.friends.findByIdAndOwnerForUpdate(friendId, memberId)
			.orElseThrow(() -> new CustomException(FriendErrorCode.NOT_FOUND));

		// ADR-009 결정 2: 본인 입력이 대리 입력을 이김. 수신자가 직접 답한 취향은 발송자의 수정으로 덮지 않음 —
		// 덮으면 추천의 근거였던 답변이 조용히 사라지고 다음 추천이 관계와 예산만 보는 상태로 돌아감(FIX-W002 T1).
		if (this.tasteProfiles.findByFriendId(friend.getId()).map(TasteProfile::isFromInvite).orElse(false)) {
			return false;
		}

		// ADR-009 결정 1: 사람 취향은 갱신형이라 친구당 1행을 유지함.
		// flush를 끼우지 않으면 Hibernate가 INSERT를 DELETE보다 먼저 내보내 uq_taste_friend를 위반함
		this.tasteProfiles.deleteByFriendId(friend.getId());
		this.tasteProfiles.flush();
		this.tasteProfiles.save(TasteProfile.forFriend(friend.getId(), toAnswersJson(summary)));
		return true;
	}

	/**
	 * 설문 링크 발급임(FEAT-W001). **토큰은 멱등이고 질문 선별(FEAT-W003)만 덮어씀** — 토글을 고쳐 다시 저장해도 이미 보낸 링크가
	 * 살아 있어야 함.
	 */
	@Transactional
	public SurveyLink issueSurveyToken(Long memberId, Long friendId, List<String> axes) {
		// 삭제·제출과 같은 행 잠금을 씀 — 안 그러면 이 UPDATE가 동시 삭제를 되살림(정적 UPDATE가 읽어둔 이름·deletedAt을 다시
		// 씀)
		Friend friend = this.friends.findByIdAndOwnerForUpdate(friendId, memberId)
			.orElseThrow(() -> new CustomException(FriendErrorCode.NOT_FOUND));

		// 축을 안 보낸 호출은 저장값을 건드리지 않음 — 기본값으로 덮으면 구형 화면의 재호출이 발송자의 선택을 세 축으로 되돌림
		if (axes != null) {
			friend.selectSurveyAxes(SurveyAxes.format(axes));
		}

		return new SurveyLink(friend.issueSurveyToken(Tokens.issue()), SurveyAxes.parse(friend.getSurveyAxes()));
	}

	/** 발급 결과임. `axes`는 정규화된 질문 선별이라 화면이 저장 결과를 되받아 그림. */
	public record SurveyLink(String token, List<String> axes) {
	}

	/**
	 * FR-005 친구 수정임. **이름만 바꾼다** — 요구사항이 "전화번호가 바뀌는 수정은 다른 사람으로 간주해 새 등록을 안내한다"이므로 번호를
	 * 덮어쓰는 경로 자체를 만들지 않음. 화면이 폼 전체를 보내므로 번호도 함께 받아 같은지 확인만 함.
	 */
	@Transactional
	public Friend rename(Long memberId, Long friendId, String rawName, String rawPhone) {
		if (friendId == null) {
			throw new CustomException(FriendErrorCode.NOT_FOUND);
		}
		Friend friend = this.friends.findByIdAndOwnerMemberIdAndDeletedAtIsNull(friendId, memberId)
			.orElseThrow(() -> new CustomException(FriendErrorCode.NOT_FOUND));

		String name = (rawName == null) ? "" : rawName.trim();
		if (name.isEmpty() || name.length() > NAME_MAX) {
			throw new CustomException(FriendErrorCode.INVALID_NAME);
		}

		String phone = Phones.digits(rawPhone);
		if (!Phones.isValid(phone)) {
			throw new CustomException(FriendErrorCode.INVALID_PHONE);
		}
		// 하이픈 표기가 달라지는 것은 허용하고 숫자가 달라지면 거절함
		if (!phone.equals(friend.getPhone())) {
			throw new CustomException(FriendErrorCode.PHONE_NOT_EDITABLE);
		}

		friend.rename(name);
		return friend;
	}

	/**
	 * gift는 friend_id FK가 필수임. 이름으로 찾고 없으면 만듦(전화번호는 나중에 채움) — 발송 흐름에서 수신자 연락처를 먼저 요구하지 않기
	 * 위한 프로토타입 규칙임.
	 */
	@Transactional
	public Long resolveIdByName(Long memberId, String name) {
		return this.friends.findFirstByOwnerMemberIdAndNameAndDeletedAtIsNull(memberId, name)
			.map(Friend::getId)
			.orElseGet(() -> this.friends.saveAndFlush(new Friend(memberId, name, null)).getId());
	}

	/**
	 * 소유 검증임. 발송 요청이 friendId를 실어 보낼 때 남의 친구로 선물이 가는 것을 막음.
	 *
	 * 없는 ID와 남의 ID를 같은 404로 다룸 — 갈라주면 남의 친구 ID를 순번으로 훑어 존재 여부를 알아낼 수 있음. 추천 소유 검증과 같은
	 * 모양임.
	 */
	@Transactional(readOnly = true)
	public Long requireOwned(Long memberId, Long friendId) {
		return this.friends.findByIdAndOwnerMemberIdAndDeletedAtIsNull(friendId, memberId)
			.map(Friend::getId)
			.orElseThrow(() -> new CustomException(FriendErrorCode.NOT_FOUND));
	}

	/** 살아 있는 친구의 전화번호임. 삭제된 친구는 번호가 파기돼(ADR-003) 비어 있음. */
	@Transactional(readOnly = true)
	public Optional<String> findPhoneById(Long friendId) {
		return this.friends.findById(friendId).map(Friend::getPhone);
	}

	private Map<Long, String> summariesOf(List<Friend> rows) {
		Map<Long, String> summaries = new HashMap<>();
		if (rows.isEmpty()) {
			return summaries;
		}

		for (TasteProfile profile : this.tasteProfiles.findByFriendIdIn(rows.stream().map(Friend::getId).toList())) {
			summaries.put(profile.getFriendId(), readSummary(profile.getAnswers()));
		}
		return summaries;
	}

	private String readSummary(String answersJson) {
		try {
			JsonNode node = this.objectMapper.readTree(answersJson).path("summary");
			return node.isMissingNode() ? null : node.asString();
		}
		catch (Exception ex) {
			return null;
		}
	}

	private String toAnswersJson(String summary) {
		try {
			return this.objectMapper.writeValueAsString(Map.of("summary", (summary == null) ? "" : summary));
		}
		catch (Exception ex) {
			throw new IllegalStateException("취향 요약 직렬화 실패", ex);
		}
	}

}
