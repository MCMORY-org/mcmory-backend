package com.mcmory.backend.gift;

import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class GiftController {

	private final GiftService gifts;

	private final CurrentMember currentMember;

	private final LetterImageService letterImages;

	public GiftController(GiftService gifts, CurrentMember currentMember, LetterImageService letterImages) {
		this.gifts = gifts;
		this.currentMember = currentMember;
		this.letterImages = letterImages;
	}

	/**
	 * `friendId`가 수신자 식별의 정본이고 `friendName`은 폴백임. 둘 다 받는 이유는 친구 등록 화면을 거치지 않는 발송 경로가 실재하기
	 * 때문임 — 그 경로는 id를 가질 수 없음. **id가 오면 이름은 무시함**(FIX-W001 T1).
	 */
	public record SendRequest(Long productId, Long recommendationId, String letterBody, Long friendId,
			String friendName, List<String> letterImageUrls, String letterColor) {
	}

	public record ChangeRequest(String reason) {
	}

	/** FR-013 선물 발송임. */
	@PostMapping("/api/v1/gift")
	public CustomResponse<GiftService.Sent> send(@RequestBody SendRequest request) {
		return CustomResponse.ok(this.gifts.send(this.currentMember.requireId(), request.productId(),
				request.recommendationId(), request.letterBody(), request.friendId(), request.friendName(),
				request.letterImageUrls(), request.letterColor()));
	}

	/**
	 * FR-012 편지 사진 업로드임. 발송과 분리한 이유는 작성 화면에서 사진을 먼저 붙이고 나중에 보내기 때문임 — 발송 한 번에 multipart로
	 * 묶으면 미리보기 화면이 파일을 다시 들고 있어야 함.
	 */
	@PostMapping("/api/v1/gift/letter-images")
	public CustomResponse<Map<String, Object>> uploadLetterImages(
			@RequestPart(value = "files", required = false) List<MultipartFile> files) {
		this.currentMember.requireId();
		return CustomResponse.ok(Map.of("urls", this.letterImages.store(files)));
	}

	/**
	 * 비회원 초대 열람임(ADR-006, ADR-013 결정 4). 로그인 없이 진입하는 유일한 API 경로라 인증을 요구하지 않음.
	 */
	@GetMapping("/api/v1/g/{token}")
	public CustomResponse<GiftService.InviteView> read(@PathVariable String token) {
		return CustomResponse.ok(this.gifts.read(token));
	}

	/** 동의와 열람임. 동의 전에는 본문을 주지 않으므로 이 호출이 열람의 전제임(FR-015). */
	@PostMapping("/api/v1/g/{token}")
	public CustomResponse<Map<String, Object>> consent(@PathVariable String token) {
		this.gifts.consentAndOpen(token);
		return CustomResponse.ok(Map.of("ok", true));
	}

	/**
	 * FR-020 "내 제품으로 등록"임. **경로는 `/api/v1/g/**` 아래지만 이것만 로그인이 필요함** — 보유 제품에 주인이 있어야 하기
	 * 때문임. 시큐리티가 경로 인가를 하지 않으므로(SecurityConfig) `requireId()`가 유일한 문지기임. 지우면 곧 인증 구멍임.
	 */
	@PostMapping("/api/v1/g/{token}/owned")
	public CustomResponse<GiftService.OwnedFromGift> registerAsOwned(@PathVariable String token) {
		return CustomResponse.ok(this.gifts.registerAsOwned(this.currentMember.requireId(), token));
	}

	/** FR-018 옵션 변경 문의임. 동의·열람과 같은 비회원 경로라 로그인을 요구하지 않음. */
	@PostMapping("/api/v1/g/{token}/change-request")
	public CustomResponse<GiftService.ChangeRequested> requestChange(@PathVariable String token,
			@RequestBody ChangeRequest request) {
		return CustomResponse.ok(this.gifts.requestChange(token, request.reason()));
	}

}
