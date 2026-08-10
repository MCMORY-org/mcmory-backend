package com.mcmory.backend.friend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mcmory.backend.global.apiPayload.code.FriendErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.common.Phones;
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
	public record FriendView(Long id, String name, String phone, String tasteSummary) {
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
		Friend friend = this.friends.findByIdAndOwnerMemberIdAndDeletedAtIsNull(friendId, memberId)
			.orElseThrow(() -> new CustomException(FriendErrorCode.NOT_FOUND));

		friend.erase();
		// ADR-003 결정 4: 취향도 함께 파기함
		this.tasteProfiles.deleteByFriendId(friend.getId());
	}

	@Transactional
	public void saveTaste(Long memberId, Long friendId, String summary) {
		Friend friend = this.friends.findByIdAndOwnerMemberIdAndDeletedAtIsNull(friendId, memberId)
			.orElseThrow(() -> new CustomException(FriendErrorCode.NOT_FOUND));

		// ADR-009 결정 9: 재저장은 덮어쓰기임. 친구당 1행을 유지함.
		// flush를 끼우지 않으면 Hibernate가 INSERT를 DELETE보다 먼저 내보내 uq_taste_friend를 위반함
		this.tasteProfiles.deleteByFriendId(friend.getId());
		this.tasteProfiles.flush();
		this.tasteProfiles.save(TasteProfile.forFriend(friend.getId(), toAnswersJson(summary)));
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
