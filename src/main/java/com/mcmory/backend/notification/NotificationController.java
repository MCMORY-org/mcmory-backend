package com.mcmory.backend.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import com.mcmory.backend.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@SecurityRequirement(name = OpenApiConfig.ACCESS_COOKIE)
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "알림",
		description = "알림 목록 조회와 전체 읽음 처리만 제공함. 개별 읽음 처리와 페이지네이션은 제공하지 않으며 목록 전체를 한 번에 반환함. `type`은 수신자용 `GIFT_ARRIVED`, 발송자용 `GIFT_OPENED`, 옵션 변경 문의용 `CHANGE_REQ_SIZE`·`CHANGE_REQ_COLOR`·`CHANGE_REQ_ALREADY_OWNED`·`CHANGE_REQ_ETC`임. 옵션 변경 문의는 `CHANGE_REQ_` 뒤의 값이 사유이므로 해당 접두사로 분기할 것. 실패는 `message`가 아니라 응답의 `code`로 분기할 것.")
public class NotificationController {

	private final NotificationRepository notifications;

	private final CurrentMember currentMember;

	public NotificationController(NotificationRepository notifications, CurrentMember currentMember) {
		this.notifications = notifications;
		this.currentMember = currentMember;
	}

	/** 명세서 5.8 #26의 `items` 원소임. 응답 전용이라 요청 본문으로 쓰이지 않음. */
	@Schema(name = "NotificationView", description = "알림 한 건임. #5.8 `GET /api/v1/notifications`의 `items` 원소이며 응답 전용임")
	public record NotificationView(
			@Schema(description = "알림 식별자임. `items`는 이 값의 내림차순으로 고정 정렬되며 페이지네이션이 없어 전체가 한 번에 옴", example = "12",
					requiredMode = Schema.RequiredMode.REQUIRED) Long id,
			@Schema(description = "알림 종류임. `GIFT_ARRIVED`는 수신자에게, 나머지는 발송자에게 감. "
					+ "**접두사가 `CHANGE_REQ_`면 뒷부분이 옵션 변경 문의 사유임** — 사유 전용 필드 없이 타입에 실은 결정이라 "
					+ "프론트가 이 접두사로 갈라 읽어야 함. 사유 4종은 SIZE·COLOR·ALREADY_OWNED·ETC임", example = "GIFT_OPENED",
					allowableValues = {
							"GIFT_ARRIVED", "GIFT_OPENED", "CHANGE_REQ_SIZE", "CHANGE_REQ_COLOR",
							"CHANGE_REQ_ALREADY_OWNED", "CHANGE_REQ_ETC" },
					requiredMode = Schema.RequiredMode.REQUIRED) String type,
			@Schema(description = "알림이 가리키는 선물의 식별자임. 알림을 눌렀을 때 이동할 대상을 잇는 데 씀", example = "7",
					requiredMode = Schema.RequiredMode.REQUIRED) Long giftId,
			@Schema(description = "알림 생성 시각임. 오프셋 없는 로컬 시각 문자열이라 프론트가 표준시를 붙여 해석하지 말 것",
					example = "2026-08-11T10:20:30",
					requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
			@Schema(description = "읽은 시각임. **`null`이면 미읽음이고 이 개수가 곧 `unread`임.** "
					+ "개별 읽음 경로가 없어 `PATCH /api/v1/notifications` 전체 읽음으로만 채워지므로 선택임", example = "2026-08-11T09:30:00",
					nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime readAt){
	}

	@Operation(summary = "알림 목록과 미읽음 수 조회",
			description = "**인증 필수** — 세션이 없으면 `AUTH401_1`임. `items`는 `id` 내림차순 고정이고 페이지네이션이 없어 전체를 한 번에 반환함. `unread`는 서버가 전체를 읽어 세므로 알림이 아주 많아지면 응답이 커짐(시연 규모에서는 문제되지 않음). `readAt`이 `null`이면 미읽음임. `giftId`로 대상 선물을 잇고, `type` 접두사가 `CHANGE_REQ_`면 뒷부분이 옵션 변경 사유임.")
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
			description = "**인증 필수** — 세션이 없으면 `AUTH401_1`임. **요청 본문이 없음.** 개별 읽음 경로는 만들지 않아 미읽음 전체가 한 번에 처리됨. 이미 전부 읽은 상태여도 성공으로 응답함(멱등).")
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
