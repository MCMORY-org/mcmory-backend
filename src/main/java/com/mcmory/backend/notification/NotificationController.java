package com.mcmory.backend.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;
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
public class NotificationController {

	private final NotificationRepository notifications;

	private final CurrentMember currentMember;

	public NotificationController(NotificationRepository notifications, CurrentMember currentMember) {
		this.notifications = notifications;
		this.currentMember = currentMember;
	}

	public record NotificationView(Long id, String type, Long giftId, LocalDateTime createdAt, LocalDateTime readAt) {
	}

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

	@PostMapping("/read")
	@Transactional
	public CustomResponse<Map<String, Object>> readAll() {
		Long memberId = this.currentMember.requireId();
		this.notifications.findByMemberIdAndReadAtIsNull(memberId).forEach(Notification::markRead);
		return CustomResponse.ok(Map.of("ok", true));
	}

}
