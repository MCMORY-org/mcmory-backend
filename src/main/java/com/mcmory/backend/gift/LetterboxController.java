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
import io.swagger.v3.oas.annotations.media.Schema;
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

	public record SentView(
			@Schema(description = "선물 id임", example = "12", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
			@Schema(description = "초대 토큰임. `GET /api/v1/invitations/{token}`(명세서 #12) 경로에 그대로 실음",
					example = "k3n8pq2wz7ta5vh0jr4bmx91", requiredMode = Schema.RequiredMode.REQUIRED) String token,
			@Schema(description = "받는 친구 이름임. **삭제된 친구는 `\"삭제된 친구\"`로 나감**(ADR-003 개인정보 즉시 파기 — 이름과 번호가 DB에서 NULL이 됨). "
					+ "선택 — 친구 행 자체를 찾지 못하면 `null`임", example = "김민지",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) String friendName,
			@Schema(description = "수신자에게 보이는 발신자 익명 닉네임임(형용사 더하기 호저). ADR-001에 따라 발송자 실명은 어디에도 싣지 않음",
					example = "다정한 호저", requiredMode = Schema.RequiredMode.REQUIRED) String nickname,
			@Schema(description = "선물한 상품명임. 선택 — 연결된 상품이 없으면 `null`임", example = "비세토스 카드지갑",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) String productName,
			@Schema(description = "선물한 상품 id임. 화면은 이 값으로 상품 이미지를 고름. 선택 — 연결된 상품이 없으면 `null`임", example = "3",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long productId,
			@Schema(description = "선물 상태임. `SENT`(발송) 또는 `OPENED`(수신자가 열람) 둘뿐임. ADR-011에 따라 역방향 전이는 없음",
					example = "OPENED", allowableValues = {
							"SENT", "OPENED" },
					requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(description = "수신자의 **최초** 열람 시각임(재방문은 갈아치우지 않음). 선택 — 아직 열지 않았으면 `null`임. "
					+ "발송분에서 이 값이 있는 건수가 `unread`임", example = "2026-08-11T14:03:21",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime openedAt){
	}

	/**
	 * 받은 선물임. ADR-001에 따라 발신자 쪽에서 나가는 값은 익명 닉네임 하나뿐임 — senderMemberId, 발송자 실명, friendId,
	 * friend.name은 싣지 않음. friend.name은 발송자가 수신자를 부르는 이름이라 발송자의 명명 습관이 새는 통로가 됨.
	 *
	 * letterBody도 없음. 본문은 동의 게이트를 지나는 `/api/v1/invitations/{token}`에서만 제공함(FR-015).
	 *
	 * **초대 토큰도 싣지 않음**(FIX-W004 ②). 토큰은 양도 가능한 자격이고 그 경로가 비인증이라, 발송자가 번호를 오타 내 엉뚱한 회원이
	 * 수신자로 매칭되면 그 회원이 링크를 넘겨 임의의 제3자가 편지 전문과 사진을 열 수 있음. 수신자는 발송자가 보낸 링크로 편지를 엶. 편지함 화면을
	 * 만들게 되면 인증된 수신자 전용 열람 경로를 신설할 것 — 토큰을 되돌리지 말 것.
	 */
	public record ReceivedView(
			@Schema(description = "선물 id임", example = "7", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
			@Schema(description = "발신자 익명 닉네임임(형용사 더하기 호저). **수신분에 나가는 발신자 정보는 이것 하나뿐임**(ADR-001) — "
					+ "`senderMemberId`·발송자 실명·`friendId`·`friend.name`은 싣지 않음", example = "느긋한 호저",
					requiredMode = Schema.RequiredMode.REQUIRED) String nickname,
			@Schema(description = "받은 상품명임. 선택 — 연결된 상품이 없으면 `null`임", example = "비세토스 미니백",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) String productName,
			@Schema(description = "받은 상품 id임. 화면은 이 값으로 상품 이미지를 고름. 선택 — 연결된 상품이 없으면 `null`임", example = "5",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long productId,
			@Schema(description = "선물 상태임. `SENT`(발송) 또는 `OPENED`(내가 열람) 둘뿐임. ADR-011에 따라 역방향 전이는 없음", example = "SENT",
					allowableValues = {
							"SENT", "OPENED" },
					requiredMode = Schema.RequiredMode.REQUIRED) String status,
			@Schema(description = "발송 시각임", example = "2026-08-10T09:12:00",
					requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime sentAt,
			@Schema(description = "내가 **최초로** 연 시각임(재방문은 갈아치우지 않음). 선택 — 아직 열지 않았으면 `null`이고, "
					+ "수신분에서 `null`인 건수가 `receivedUnopened`임", example = "2026-08-11T14:03:21", nullable = true,
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime openedAt){
	}

	@GetMapping
	@Operation(summary = "편지함 조회",
			description = """
					로그인한 회원의 발송분(`sent`)과 수신분(`received`)을 함께 반환함. 인증 필수임 — 세션이 없으면 `AUTH401_1`임.

					함정 1: `unread`는 **발송분** 기준(수신자가 열어본 건수)이고 `receivedUnopened`는 **수신분** 기준(내가 아직 안 연 건수)임. 뜻이 서로 반대라 한 이름으로 합치지 않았으니 혼동하지 말 것(명세서 5.4의 #14).

					함정 2: `received` 항목에 나가는 발신자 정보는 익명 닉네임 하나뿐임(ADR-001). `senderMemberId`·발송자 실명·`friendId`·`friend.name`은 싣지 않음.

					함정 3: **편지 본문(`letterBody`)은 목록에 없음.** 본문은 동의 게이트를 지나는 `GET /api/v1/invitations/{token}`(명세서 #12)에서만 제공함(FR-015). **수신분에는 그 `token`도 없음** — 토큰은 양도 가능한 자격이고 그 경로가 비인증이라, 번호 오타로 오귀속된 회원이 링크를 넘기면 제3자가 편지를 열게 됨. 수신자는 발송자가 보낸 링크로 편지를 엶. 발송분(`sent`)에는 URL 복사를 위해 남아 있음.

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
					        "token": "k3n8pq2wz7ta5vh0jr4bmx91",
					        "friendName": "김민지",
					        "nickname": "다정한 호저",
					        "productName": "비세토스 카드지갑",
					        "productId": 3,
					        "status": "OPENED",
					        "openedAt": "2026-08-11T14:03:21"
					      }
					    ],
					    "unread": 1,
					    "received": [
					      {
					        "id": 7,
					        "nickname": "느긋한 호저",
					        "productName": "비세토스 미니백",
					        "productId": 5,
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

			// productId는 product가 아니라 gift에서 꺼냄 — 상품이 삭제돼도 id는 남아야 화면이 이미지를 고를 수 있음
			return new SentView(gift.getId(), gift.getInviteToken(), friendName, gift.getAnonNickname(),
					(product == null) ? null : product.getName(), gift.getProductId(), gift.getStatus(),
					gift.getOpenedAt());
		}).toList();

		List<ReceivedView> received = this.gifts.findByRecipientMemberIdOrderByIdDesc(memberId).stream().map((gift) -> {
			Product product = productOf(gift);

			return new ReceivedView(gift.getId(), gift.getAnonNickname(), (product == null) ? null : product.getName(),
					gift.getProductId(), gift.getStatus(), gift.getSentAt(), gift.getOpenedAt());
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
