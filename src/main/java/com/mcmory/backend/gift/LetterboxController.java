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
