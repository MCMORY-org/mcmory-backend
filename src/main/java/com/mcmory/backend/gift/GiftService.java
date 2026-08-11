package com.mcmory.backend.gift;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mcmory.backend.global.apiPayload.code.FriendErrorCode;
import com.mcmory.backend.global.apiPayload.code.GiftErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.common.Tokens;
import com.mcmory.backend.consent.Consent;
import com.mcmory.backend.consent.ConsentRepository;
import com.mcmory.backend.friend.FriendService;
import com.mcmory.backend.member.Member;
import com.mcmory.backend.member.MemberRepository;
import com.mcmory.backend.notification.Notification;
import com.mcmory.backend.notification.NotificationRepository;
import com.mcmory.backend.owned.OwnedProduct;
import com.mcmory.backend.owned.OwnedProductRepository;
import com.mcmory.backend.product.Product;
import com.mcmory.backend.product.ProductRepository;
import com.mcmory.backend.recommend.RecommendService;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GiftService {

	/** ADR-001: 수신자에게 보일 익명 닉네임의 후보임. 전부 쓰이면 숫자 접미사로 넘어감. */
	private static final List<String> ADJECTIVES = List.of("멋진", "잘생긴", "예쁜", "활발한", "다정한", "든든한");

	private final GiftRepository gifts;

	private final ProductRepository products;

	private final ConsentRepository consents;

	private final NotificationRepository notifications;

	private final FriendService friends;

	private final RecommendService recommendations;

	private final MemberRepository members;

	private final OwnedProductRepository owned;

	private final ObjectMapper objectMapper;

	private final LetterImageService letterImages;

	public GiftService(GiftRepository gifts, ProductRepository products, ConsentRepository consents,
			NotificationRepository notifications, FriendService friends, RecommendService recommendations,
			MemberRepository members, OwnedProductRepository owned, ObjectMapper objectMapper,
			LetterImageService letterImages) {
		this.owned = owned;
		this.objectMapper = objectMapper;
		this.letterImages = letterImages;
		this.gifts = gifts;
		this.products = products;
		this.consents = consents;
		this.notifications = notifications;
		this.friends = friends;
		this.recommendations = recommendations;
		this.members = members;
	}

	public record Sent(
			@Schema(description = "초대 토큰임. 이 값으로 `/g/{token}` 초대 URL을 만들어 수신자에게 전달함(FR-014)",
					example = "k3n8pq2wz7ta5vh0jr4bmx91") String token,
			@Schema(description = "수신자에게 보일 발송자의 익명 닉네임임(ADR-001). 발송자 실명은 주지 않음",
					example = "다정한 호저") String nickname) {
	}

	/**
	 * 동의 전에는 편지 본문과 상품을 담지 않음(FR-015). needConsent가 그 분기임.
	 *
	 * NON_NULL이 계약임 — null을 실어 보내면 수신 측에서 "값이 null인 필드"와 "없는 필드"가 구별되지 않음. 동의 전 응답에
	 * letterBody 키 자체가 없어야 한다는 것이 프로토타입 계약이고 스모크가 그것을 봄.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record InviteView(
			@Schema(description = "동의가 아직 필요한지임. `true`면 닉네임만 오고 아래 본문·상품 키가 통째로 없음. "
					+ "`false`로 바꾸려면 #13 `POST /api/v1/g/{token}` 동의를 먼저 거쳐야 함(FR-015)",
					example = "true") boolean needConsent,
			@Schema(description = "발송자의 익명 닉네임임(ADR-001). 동의 전후 모두 옴", example = "다정한 호저") String nickname,
			@Schema(description = "편지 본문임. **동의 전에는 키 자체가 없음**(FR-015). 1자 이상 200자 이하로 저장된 값임",
					example = "생일 축하해. 늘 고마워", nullable = true) String letterBody,
			@Schema(description = "편지에 붙은 사진 URL 목록임. 사진을 넣어 보냈을 때만 붙고 **없으면 키 자체가 없음**. "
					+ "최대 5개이고 전부 `/letter-images/`로 시작함",
					example = "[\"/letter-images/8f0c1d2e111122223333444455556666.jpg\"]",
					nullable = true) List<String> letterImageUrls,
			@Schema(description = "편지지 **배경색 토큰**임(글자색이 아니므로 대비는 화면이 배경 명도로 판단함). "
					+ "발송자가 색을 골랐을 때만 붙고 **없으면 키 자체가 없음**. 취향 색상 6종과는 다른 축임", example = "GOLD",
					allowableValues = {
							"GOLD", "BLACK", "BEIGE", "PINK" },
					nullable = true) String letterColor,
			@Schema(description = "선물로 보낸 상품임. **동의 전에는 키 자체가 없고**, 연결된 상품이 없으면 동의 후에도 없음",
					nullable = true) ProductView product,
			@Schema(description = "최초 열람 시각임. 동의와 열람(#13) 전에는 키 자체가 없고 **재방문해도 갈아치우지 않음**(FR-016)",
					example = "2026-08-11T13:05:00", nullable = true) LocalDateTime openedAt){
	}

	/**
	 * FR-012 편지지 색상임. **배경색이지 글자색이 아니다** — 대비를 맞추는 것은 화면 몫이다.
	 *
	 * 색값이 아니라 토큰을 저장하는 이유는 실제 hex를 디자인이 쥐고 있어서임 — 팔레트 색이 바뀌어도 저장값과 스키마를 안 건드린다. 자유 문자열은
	 * 화면의 style 속성에 그대로 들어가 주입 통로가 되므로 허용 목록으로만 받는다.
	 */
	private static final Set<String> LETTER_COLORS = Set.of("GOLD", "BLACK", "BEIGE", "PINK");

	private String normalizeLetterColor(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String color = raw.trim().toUpperCase(Locale.ROOT);
		if (!LETTER_COLORS.contains(color)) {
			throw new CustomException(GiftErrorCode.INVALID_LETTER_COLOR);
		}
		return color;
	}

	public record ProductView(@Schema(description = "상품 id임. 화면은 이 값으로 상품 이미지를 고름", example = "1") Long productId,
			@Schema(description = "상품 이름임. 스냅샷이 아니라 현재 값이라 상품이 바뀌면 바뀐 값이 보임", example = "숄더백") String name,
			@Schema(description = "상품 가격임. 이름과 마찬가지로 스냅샷이 아니라 현재 값임. 값이 없는 상품은 `0`으로 내려감. "
					+ "**단위는 원임** — 추천 요청의 예산(만원 단위)과 다르므로 그대로 비교하지 말 것", example = "890000") int price,
			@Schema(description = "상품 이미지 URL임. **있으면 이 값을 그대로 쓰고, `null`이면 화면이 자체 규약(`/products/{id}.webp`)으로 고를 것.** "
					+ "MCM 공식 CDN 주소라 우리 서버를 거치지 않음. 옛 시드 상품은 아직 `null`임",
					example = "https://images.mcmworldwide.com/i/mcmworldwide/MMRGATA07BK001_01/MMRGATA07BK001?$large$&fmt=auto&qlt=default",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) String imageUrl) {
	}

	public record OwnedFromGift(
			@Schema(description = "생성되었거나 이미 있던 보유 제품 id임. 재호출해도 새 행을 만들지 않고 같은 id를 줌(멱등)", example = "1") Long id,
			@Schema(description = "보유 제품의 등록 경로임. 이 API로 만든 행은 `GIFT` 고정임(데모 시리얼 등록과 구분하는 값임)",
					example = "GIFT") String source,
			@Schema(description = "등록된 상품임. 상품은 초대 토큰이 이미 정하므로 요청이 상품을 다시 지정하지 않음") ProductView product) {
	}

	/**
	 * FR-012: 발송에 실린 사진 URL을 JSON 배열로 굳힘. **우리 업로드 경로가 발급한 URL만 받는다** — 임의 문자열을 허용하면 수신자
	 * 화면이 외부 이미지를 그대로 그리게 되고, 그건 발송자가 남의 페이지에 흔적을 남기는 통로가 됨.
	 */
	private String writeImageUrls(List<String> urls) {
		if (urls == null || urls.isEmpty()) {
			return null;
		}
		if (urls.size() > LetterImageService.MAX_COUNT) {
			throw new CustomException(GiftErrorCode.TOO_MANY_IMAGES);
		}
		for (String url : urls) {
			// 접두사만 보면 `/letter-images/../api/v1/auth/me` 같은 값이 통과함 — 형태와 실물 존재를 함께 확인함
			if (!this.letterImages.isIssuedUrl(url)) {
				throw new CustomException(GiftErrorCode.INVALID_IMAGE);
			}
		}
		return this.objectMapper.writeValueAsString(urls);
	}

	private List<String> readImageUrls(Gift gift) {
		if (gift.getLetterImageUrls() == null || gift.getLetterImageUrls().isBlank()) {
			return null;
		}
		List<String> urls = new ArrayList<>();
		for (JsonNode node : this.objectMapper.readTree(gift.getLetterImageUrls())) {
			urls.add(node.asString());
		}
		return urls.isEmpty() ? null : urls;
	}

	/** FR-018 문의 사유임. ADR-007 결정 2의 고정 4종. */
	private static final List<String> CHANGE_REASONS = List.of("SIZE", "COLOR", "ALREADY_OWNED", "ETC");

	public record ChangeRequested(
			@Schema(description = "접수된 문의 사유임. 요청값을 앞뒤 공백 제거와 대문자화까지 마친 정규화 결과라 "
					+ "소문자로 보내도 대문자로 돌아옴. 발송자에게는 알림 `type`이 `CHANGE_REQ_SIZE` 형태로 감", example = "SIZE",
					allowableValues = {
							"SIZE", "COLOR", "ALREADY_OWNED", "ETC" }) String reason,
			@Schema(description = "수신자에게 함께 주는 공식몰 링크임(ADR-007 경로 2). " + "연결된 상품이 없거나 상품에 링크가 없으면 값이 없음",
					nullable = true) String officialUrl){
	}

	/**
	 * FR-018: 수신자가 사유를 골라 발송자에게 문의함. 선물당 1회이고, 발송자는 알림 타입으로 사유를 봄.
	 *
	 * 비회원 경로라 인증을 요구하지 않음 — 초대 토큰이 리소스 식별자이자 권한 근거임(ADR-013 결정 4, 동의 경로와 같은 모델).
	 */
	@Transactional
	public ChangeRequested requestChange(String inviteToken, String rawReason) {
		String reason = (rawReason == null) ? "" : rawReason.trim().toUpperCase();
		if (!CHANGE_REASONS.contains(reason)) {
			throw new CustomException(GiftErrorCode.INVALID_CHANGE_REASON);
		}

		Gift gift = this.gifts.findByInviteTokenForUpdate(inviteToken)
			.orElseThrow(() -> new CustomException(GiftErrorCode.INVITE_NOT_FOUND));

		if (gift.getOpenedAt() == null) {
			throw new CustomException(GiftErrorCode.NOT_OPENED);
		}
		if (gift.getChangeRequestedAt() != null) {
			throw new CustomException(GiftErrorCode.ALREADY_CHANGE_REQUESTED);
		}

		gift.requestChange();
		this.notifications.save(Notification.changeRequested(gift.getSenderMemberId(), gift.getId(), reason));

		// 경로 2: 수신자에게 공식몰 링크를 함께 줌(ADR-007). 상품이 없으면 링크만 빈 채로 나감
		String officialUrl = (gift.getProductId() == null) ? null
				: this.products.findById(gift.getProductId()).map(Product::getOfficialUrl).orElse(null);

		return new ChangeRequested(reason, officialUrl);
	}

	/**
	 * FR-020: Unwrap에서 "내 제품으로 등록"임. 상품은 토큰이 이미 정하므로 productId를 다시 받지 않음 — 받으면 남의 선물 토큰에
	 * 아무 상품이나 실어 등록하는 경로가 생김.
	 *
	 * 행을 잠그는 이유는 더블클릭으로 보유 제품이 두 줄 생기는 것을 막기 위함임. gift_id 유니크 제약은 쓰지 않음(soft delete된 행이
	 * 자리를 영구 점유해 삭제 후 재등록이 막힘).
	 */
	@Transactional
	public OwnedFromGift registerAsOwned(Long memberId, String inviteToken) {
		Gift gift = this.gifts.findByInviteTokenForUpdate(inviteToken)
			.orElseThrow(() -> new CustomException(GiftErrorCode.INVITE_NOT_FOUND));

		// 다른 회원에게 지정된 선물은 존재 자체를 알리지 않음 — 없는 초대와 같은 응답임
		if (gift.getRecipientMemberId() != null && !gift.getRecipientMemberId().equals(memberId)) {
			throw new CustomException(GiftErrorCode.INVITE_NOT_FOUND);
		}
		if (gift.getOpenedAt() == null) {
			throw new CustomException(GiftErrorCode.NOT_OPENED);
		}

		Product product = (gift.getProductId() == null) ? null
				: this.products.findById(gift.getProductId()).orElse(null);
		if (product == null) {
			throw new CustomException(GiftErrorCode.PRODUCT_NOT_FOUND);
		}

		// FIX-W004 ①: 번호 매칭이 없던 선물을 여기서 귀속함. 등록이 이 흐름의 유일한 인증 지점이라
		// 여기서 안 채우면 받은 편지함(recipient_member_id 조회)이 영영 비어 있음.
		// 알림은 만들지 않음 — FR-017이 도착 알림을 발송 시점 1회로 못박았고, 여기서 만들면 수신자 본인에게 뒤늦게 뜸
		gift.claimRecipient(memberId);

		// 재클릭은 새 행을 만들지 않고 기존 행을 그대로 돌려줌(멱등)
		OwnedProduct owned = this.owned.findByGiftIdAndDeletedAtIsNull(gift.getId())
			.orElseGet(() -> this.owned.save(OwnedProduct.fromGift(memberId, product.getId(), gift.getId())));

		return new OwnedFromGift(owned.getId(), owned.getSource(), new ProductView(product.getId(), product.getName(),
				(product.getPrice() == null) ? 0 : product.getPrice(), product.getImageUrl()));
	}

	/** FR-013 선물 발송임. ADR-011에 따라 발송 시점에 SENT로 확정함. */
	@Transactional
	public Sent send(Long memberId, Long productId, Long recommendationId, String rawLetterBody, Long rawFriendId,
			String friendName, List<String> letterImageUrls, String rawLetterColor) {
		if (productId == null) {
			throw new CustomException(GiftErrorCode.PRODUCT_NOT_FOUND);
		}
		// 추천 ID는 선택 입력임. 오면 소유와 함께 **그 추천이 실제로 이 상품을 담고 있는지**까지 확인함(FIX-W004 ③)
		if (recommendationId != null) {
			this.recommendations.requireContainsProduct(memberId, recommendationId, productId);
		}
		Product product = this.products.findById(productId)
			.orElseThrow(() -> new CustomException(GiftErrorCode.PRODUCT_NOT_FOUND));

		// 추천만 막으면 뚫림 — recommendationId가 선택 필드라 없으면 여기가 유일한 문지기임.
		// 코디용 의류 id를 직접 실으면 발송과 열람과 보유 등록까지 그대로 감.
		// 없는 상품과 같은 코드를 씀 — 어느 id가 선물 가능한지 훑게 하지 않기 위함임
		if (!product.isGiftEligible()) {
			throw new CustomException(GiftErrorCode.PRODUCT_NOT_FOUND);
		}

		String letterBody = (rawLetterBody == null) ? "" : rawLetterBody.trim();
		// ADR-008: 편지 본문 1자 이상 200자 이하. v1.0 디자인 14는 500자였는데 v1.1 Unwrap-01이 200자로
		// 줄여 새 버전에 맞춤(screen-inventory 0.3). 화면 표기가 `78/200자`라 사용자가 보는 숫자와 서버 한도가 같아야 함
		if (letterBody.isEmpty() || letterBody.length() > 200) {
			throw new CustomException(GiftErrorCode.INVALID_LETTER_LENGTH);
		}

		// 발송 흐름은 친구 등록 화면을 거치지 않아 FriendService.create()의 검증을 못 받음. 이름을 안 쓰면 익명 수신자로 두되
		// 길이만은 여기서 막아야 함 — 컬럼이 VARCHAR(20)이라 넘치면 COMMON500으로 샘
		String recipientName = (friendName == null) ? "" : friendName.trim();
		if (recipientName.isEmpty()) {
			recipientName = "친구";
		}
		if (recipientName.length() > FriendService.NAME_MAX) {
			throw new CustomException(FriendErrorCode.INVALID_NAME);
		}

		// FIX-W001 T1: id가 오면 그것이 정본임. 이름으로 찾던 시절에는 한 글자만 틀려도 번호 없는 친구 행이 새로 생겨
		// 아래 recipientMemberId 해석이 항상 null이 됐고, 도착 알림(FR-017)이 조용히 끊겼음
		Long friendId = (rawFriendId == null) ? this.friends.resolveIdByName(memberId, recipientName)
				: this.friends.requireOwned(memberId, rawFriendId);
		Long recipientMemberId = resolveRecipientMember(friendId);

		Gift gift = Gift.send(memberId, friendId, recipientMemberId, product.getId(), recommendationId, issueToken(),
				issueNickname(), letterBody, writeImageUrls(letterImageUrls), normalizeLetterColor(rawLetterColor));
		this.gifts.save(gift);

		// FR-017: 도착 알림은 발송 시점에만 만듦. 나중에 가입한 사람에게 소급 생성하지 않음
		if (recipientMemberId != null) {
			this.notifications.save(Notification.giftArrived(recipientMemberId, gift.getId()));
		}

		// FR-014: 공유할 초대 URL을 만들 토큰을 함께 돌려줌
		return new Sent(gift.getInviteToken(), gift.getAnonNickname());
	}

	/**
	 * ADR-013 결정 4: 비회원 경로임. 초대 토큰은 인증 주체가 아니라 리소스 식별자라 시큐리티 필터가 아니라 여기(서비스 계층)에서 조회로
	 * 검증함.
	 */
	@Transactional(readOnly = true)
	public InviteView read(String inviteToken) {
		Gift gift = this.gifts.findByInviteToken(inviteToken)
			.orElseThrow(() -> new CustomException(GiftErrorCode.INVITE_NOT_FOUND));

		if (!gift.isConsented()) {
			return new InviteView(true, gift.getAnonNickname(), null, null, null, null, null);
		}

		Product product = (gift.getProductId() == null) ? null
				: this.products.findById(gift.getProductId()).orElse(null);

		return new InviteView(false, gift.getAnonNickname(), gift.getLetterBody(), readImageUrls(gift),
				gift.getLetterColor(),
				(product == null) ? null
						: new ProductView(product.getId(), product.getName(),
								(product.getPrice() == null) ? 0 : product.getPrice(), product.getImageUrl()),
				gift.getOpenedAt());
	}

	/** 동의와 열람임. 동의 이력은 append-only로 남기고(ADR-002) 최초 열람만 상태에 반영함(FR-016). */
	@Transactional
	public void consentAndOpen(String inviteToken) {
		// 잠그지 않으면 링크를 두 번 동시에 열었을 때 둘 다 openedAt이 null인 것을 보고 열람 알림이 2건 생김.
		// 아래 requestChange와 registerAsOwned는 이미 잠그고 있어 여기만 빠져 있었음
		Gift gift = this.gifts.findByInviteTokenForUpdate(inviteToken)
			.orElseThrow(() -> new CustomException(GiftErrorCode.INVITE_NOT_FOUND));

		this.consents.save(Consent.recipientPrivacy(gift.getId()));

		boolean firstOpen = (gift.getOpenedAt() == null);
		gift.consentAndOpen();

		if (firstOpen) {
			this.notifications.save(Notification.giftOpened(gift.getSenderMemberId(), gift.getId()));
		}
	}

	/**
	 * 친구의 전화번호로 회원을 찾음. 이름만 있는 친구(FriendService.resolveIdByName가 만드는 것)는 번호가 없어 항상 null이고
	 * 그 선물은 비회원 토큰 흐름으로만 열림.
	 */
	private Long resolveRecipientMember(Long friendId) {
		return this.friends.findPhoneById(friendId)
			.flatMap(this.members::findByPhoneAndDeletedAtIsNull)
			.map(Member::getId)
			.orElse(null);
	}

	private String issueNickname() {
		Set<String> used = new HashSet<>(this.gifts.findAllNicknames());
		for (String adjective : ADJECTIVES) {
			String candidate = adjective + " 호저";
			if (!used.contains(candidate)) {
				return candidate;
			}
		}
		return "호저" + (used.size() + 1);
	}

	private String issueToken() {
		return Tokens.issue();
	}

}
