package com.mcmory.backend.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-017 알림임. 목록 조회와 전체 읽음 둘뿐이고 개별 읽음과 페이징은 만들지 않음 — 시연에 필요한 것은 뱃지 수와 목록이고, 시간이 밀리면 이
 * 컨트롤러가 첫 번째 컷 대상임(수신함의 receivedUnopened만으로도 시연은 성립함).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "알림",
		description = "FR-017 알림임. 목록 조회(#26)와 전체 읽음(#27) 둘뿐이고 개별 읽음과 페이징은 없음 — 전체를 한 번에 반환함. 명세서 5.8절이 계약 정본임. 절 전체가 `화면 미구현`이며 수신자 진입이 SMS 링크라 인앱 알림 없이 흐름이 시작됨. `type`은 `GIFT_ARRIVED`(수신자에게), `GIFT_OPENED`(발송자에게), 옵션 변경 문의의 `CHANGE_REQ_SIZE`·`CHANGE_REQ_COLOR`·`CHANGE_REQ_ALREADY_OWNED`·`CHANGE_REQ_ETC`(발송자에게)임. **뒤 넷은 접두사 `CHANGE_REQ_` 뒤가 사유임** — 사유 전용 컬럼 없이 타입에 실은 결정이라 프론트는 이 접두사로 갈라 읽어야 함. 실패는 문구가 아니라 `code`로 분기할 것.")
public class NotificationController {

	private final NotificationRepository notifications;

	private final CurrentMember currentMember;

	public NotificationController(NotificationRepository notifications, CurrentMember currentMember) {
		this.notifications = notifications;
		this.currentMember = currentMember;
	}

	public record NotificationView(Long id, String type, Long giftId, LocalDateTime createdAt, LocalDateTime readAt) {
	}

	@Operation(summary = "알림 목록과 미읽음 수 조회",
			description = "명세서 5.8 #26임. **인증 필수** — 세션이 없으면 `AUTH401_1`임. `items`는 `id` 내림차순 고정이고 페이지네이션이 없어 전체를 한 번에 반환함. `unread`는 서버가 전체를 읽어 세므로 알림이 아주 많아지면 응답이 커짐(시연 규모에서는 문제되지 않음). `readAt`이 `null`이면 미읽음임. `giftId`로 대상 선물을 잇고, `type` 접두사가 `CHANGE_REQ_`면 뒷부분이 옵션 변경 사유임.")
	@ApiResponse(responseCode = "200", description = "조회 성공. 봉투 포함 실제 응답임",
			content = @Content(examples = @ExampleObject(name = "성공",
					value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": {
							    "items": [
							      { "id": 12, "type": "GIFT_OPENED", "giftId": 7, "createdAt": "2026-08-11T10:20:30", "readAt": null },
							      { "id": 11, "type": "CHANGE_REQ_SIZE", "giftId": 7, "createdAt": "2026-08-11T09:05:00", "readAt": "2026-08-11T09:30:00" }
							    ],
							    "unread": 1
							  }
							}""")))
	@ApiResponse(responseCode = "401", description = "세션 없음. 로그인 화면으로 보낼 것",
			content = @Content(examples = @ExampleObject(name = "AUTH401_1", value = """
					{
					  "isSuccess": false,
					  "code": "AUTH401_1",
					  "message": "로그인이 필요합니다",
					  "result": null
					}""")))
	@GetMapping
	@Transactional(readOnly = true)
	public CustomResponse<Map<String, Object>> list() {
		Long memberId = this.currentMember.requireId();

		List<NotificationView> items = this.notifications.findByMemberIdOrderByIdDesc(memberId)
			.stream()
			.map((row) -> new NotificationView(row.getId(), row.getType(), row.getGiftId(), row.getCreatedAt(),
					row.getReadAt()))
			.toList();

		long unread = items.stream().filter((item) -> item.readAt() == null).count();
		return CustomResponse.ok(Map.of("items", items, "unread", unread));
	}

	@Operation(summary = "알림 전체 읽음 처리",
			description = "명세서 5.8 #27임. **인증 필수** — 세션이 없으면 `AUTH401_1`임. **요청 본문이 없음.** 개별 읽음 경로는 만들지 않아 미읽음 전체가 한 번에 처리됨. 이미 전부 읽은 상태여도 성공으로 응답함(멱등).")
	@ApiResponse(responseCode = "200", description = "전체 읽음 처리 성공. 봉투 포함 실제 응답임",
			content = @Content(examples = @ExampleObject(name = "성공", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": { "ok": true }
					}""")))
	@ApiResponse(responseCode = "401", description = "세션 없음. 로그인 화면으로 보낼 것",
			content = @Content(examples = @ExampleObject(name = "AUTH401_1", value = """
					{
					  "isSuccess": false,
					  "code": "AUTH401_1",
					  "message": "로그인이 필요합니다",
					  "result": null
					}""")))
	@PostMapping("/read")
	@Transactional
	public CustomResponse<Map<String, Object>> readAll() {
		Long memberId = this.currentMember.requireId();
		this.notifications.findByMemberIdAndReadAtIsNull(memberId).forEach(Notification::markRead);
		return CustomResponse.ok(Map.of("ok", true));
	}

}
