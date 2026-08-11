package com.mcmory.backend.gift;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;
import com.mcmory.backend.friend.Friend;
import com.mcmory.backend.friend.FriendRepository;
import com.mcmory.backend.product.Product;
import com.mcmory.backend.product.ProductRepository;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-017 편지함임. 발송자는 수신자의 최초 열람을 여기서 확인함.
 *
 * ponytail: 프로토타입은 회원 수신자 흐름이 없어 발송분만 보여줌. 수신함은 회원 수신자가 생기는 시점에 추가함.
 */
@RestController
@RequestMapping("/api/v1/letters")
@Tag(name = "편지함",
		description = "FR-017 편지함. 발송분과 수신분을 한 번에 반환함. 명세서 5.4의 #14가 계약 정본임. **화면 미구현**(명세서 5.0) 상태이나 API는 동작함. 페이지네이션이 없어 전체를 한 번에 반환함(명세서 6장)")
public class LetterboxController {

	private final GiftRepository gifts;

	private final FriendRepository friends;

	private final ProductRepository products;

	private final CurrentMember currentMember;

	public LetterboxController(GiftRepository gifts, FriendRepository friends, ProductRepository products,
			CurrentMember currentMember) {
		this.gifts = gifts;
		this.friends = friends;
		this.products = products;
		this.currentMember = currentMember;
	}

	public record SentView(Long id, String token, String friendName, String nickname, String productName, String emoji,
			String status, LocalDateTime openedAt) {
	}

	/**
	 * 받은 선물임. ADR-001에 따라 발신자 쪽에서 나가는 값은 익명 닉네임 하나뿐임 — senderMemberId, 발송자 실명, friendId,
	 * friend.name은 싣지 않음. friend.name은 발송자가 수신자를 부르는 이름이라 발송자의 명명 습관이 새는 통로가 됨.
	 *
	 * letterBody도 없음. 본문은 동의 게이트를 지나는 `/api/v1/g/{token}`에서만 제공함(FR-015).
	 */
	public record ReceivedView(Long id, String token, String nickname, String productName, String emoji, String status,
			LocalDateTime sentAt, LocalDateTime openedAt) {
	}

	@GetMapping
	@Operation(summary = "편지함 조회",
			description = """
					로그인한 회원의 발송분(`sent`)과 수신분(`received`)을 함께 반환함. 인증 필수임 — 세션이 없으면 `AUTH401_1`임.

					함정 1: `unread`는 **발송분** 기준(수신자가 열어본 건수)이고 `receivedUnopened`는 **수신분** 기준(내가 아직 안 연 건수)임. 뜻이 서로 반대라 한 이름으로 합치지 않았으니 혼동하지 말 것(명세서 5.4의 #14).

					함정 2: `received` 항목에 나가는 발신자 정보는 익명 닉네임 하나뿐임(ADR-001). `senderMemberId`·발송자 실명·`friendId`·`friend.name`은 싣지 않음.

					함정 3: **편지 본문(`letterBody`)은 목록에 없음.** 본문은 동의 게이트를 지나는 `GET /api/v1/g/{token}`(명세서 #12)에서만 제공함(FR-015).

					함정 4: 삭제된 친구는 `friendName`이 `"삭제된 친구"`로 보임(ADR-003 개인정보 즉시 파기).

					함정 5: 페이지네이션이 없어 전체를 한 번에 반환함(명세서 6장).""")
	@ApiResponse(responseCode = "200", description = "조회 성공. 봉투 `code`는 문자열 `\"200\"`임",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "성공", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": {
					    "sent": [
					      {
					        "id": 12,
					        "token": "aB3xY9kQ7mN2",
					        "friendName": "김민지",
					        "nickname": "다정한 호저",
					        "productName": "비세토스 카드지갑",
					        "emoji": "👛",
					        "status": "OPENED",
					        "openedAt": "2026-08-11T14:03:21"
					      }
					    ],
					    "unread": 1,
					    "received": [
					      {
					        "id": 7,
					        "token": "pQ8wZ1rT4vL6",
					        "nickname": "느긋한 호저",
					        "productName": "비세토스 미니백",
					        "emoji": "👜",
					        "status": "SENT",
					        "sentAt": "2026-08-10T09:12:00",
					        "openedAt": null
					      }
					    ],
					    "receivedUnopened": 1
					  }
					}""")))
	@ApiResponse(responseCode = "401", description = "로그인하지 않음. 문구가 아니라 `code`로 분기할 것(명세서 3장)",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AUTH401_1", value = """
					{
					  "isSuccess": false,
					  "code": "AUTH401_1",
					  "message": "로그인이 필요합니다",
					  "result": null
					}""")))
	@Transactional(readOnly = true)
	public CustomResponse<Map<String, Object>> letterbox() {
		Long memberId = this.currentMember.requireId();

		List<SentView> sent = this.gifts.findBySenderMemberIdOrderByIdDesc(memberId).stream().map((gift) -> {
			String friendName = this.friends.findById(gift.getFriendId()).map(Friend::displayName).orElse(null);
			Product product = productOf(gift);

			return new SentView(gift.getId(), gift.getInviteToken(), friendName, gift.getAnonNickname(),
					(product == null) ? null : product.getName(), (product == null) ? "🎁" : product.emoji(),
					gift.getStatus(), gift.getOpenedAt());
		}).toList();

		List<ReceivedView> received = this.gifts.findByRecipientMemberIdOrderByIdDesc(memberId).stream().map((gift) -> {
			Product product = productOf(gift);

			return new ReceivedView(gift.getId(), gift.getInviteToken(), gift.getAnonNickname(),
					(product == null) ? null : product.getName(), (product == null) ? "🎁" : product.emoji(),
					gift.getStatus(), gift.getSentAt(), gift.getOpenedAt());
		}).toList();

		// unread는 발송분 기준(수신자가 열어본 건수)이고 receivedUnopened는 수신분 기준(내가 아직 안 연 건수)임.
		// 두 수를 한 이름으로 합치지 않는 이유는 화면에서 뜻이 서로 반대이기 때문임
		long unread = sent.stream().filter((view) -> "OPENED".equals(view.status())).count();
		long receivedUnopened = received.stream().filter((view) -> view.openedAt() == null).count();

		return CustomResponse
			.ok(Map.of("sent", sent, "unread", unread, "received", received, "receivedUnopened", receivedUnopened));
	}

	private Product productOf(Gift gift) {
		return (gift.getProductId() == null) ? null : this.products.findById(gift.getProductId()).orElse(null);
	}

}
