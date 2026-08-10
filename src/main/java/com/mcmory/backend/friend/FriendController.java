package com.mcmory.backend.friend;

import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

	private final FriendService friends;

	private final CurrentMember currentMember;

	public FriendController(FriendService friends, CurrentMember currentMember) {
		this.friends = friends;
		this.currentMember = currentMember;
	}

	public record CreateRequest(String name, String phone) {
	}

	public record IdRequest(Long id) {
	}

	public record TasteRequest(Long id, String tasteSummary) {
	}

	public record UpdateRequest(String name, String phone) {
	}

	@GetMapping
	public CustomResponse<Map<String, Object>> list() {
		List<FriendService.FriendView> list = this.friends.list(this.currentMember.requireId());
		return CustomResponse.ok(Map.of("list", list));
	}

	/** FR-004 친구 등록임. */
	@PostMapping
	public CustomResponse<Map<String, Object>> create(@RequestBody CreateRequest request) {
		Friend created = this.friends.create(this.currentMember.requireId(), request.name(), request.phone());
		return CustomResponse.ok(Map.of("ok", true, "friend",
				Map.of("id", created.getId(), "name", created.getName(), "phone", created.getPhone())));
	}

	/** FR-006 삭제임. ADR-003에 따라 개인정보는 즉시 파기함. */
	@DeleteMapping
	public CustomResponse<Map<String, Object>> delete(@RequestBody IdRequest request) {
		this.friends.erase(this.currentMember.requireId(), request.id());
		return CustomResponse.ok(Map.of("ok", true));
	}

	/**
	 * FR-005 친구 수정임. 경로 변수가 있어 아래 취향 저장(`PATCH /api/v1/friends`)과 패턴이 갈리므로 매핑이 모호해지지 않음 —
	 * **취향 저장 경로를 건드리지 말 것.** 프론트가 그 계약에 이미 묶여 있음.
	 */
	@PatchMapping("/{id}")
	public CustomResponse<Map<String, Object>> rename(@PathVariable Long id, @RequestBody UpdateRequest request) {
		Friend updated = this.friends.rename(this.currentMember.requireId(), id, request.name(), request.phone());
		return CustomResponse.ok(Map.of("ok", true, "friend",
				Map.of("id", updated.getId(), "name", updated.getName(), "phone", updated.getPhone())));
	}

	/** FR-011 취향 저장 귀속임(ADR-009). 재저장은 덮어쓰기임. */
	@PatchMapping
	public CustomResponse<Map<String, Object>> saveTaste(@RequestBody TasteRequest request) {
		this.friends.saveTaste(this.currentMember.requireId(), request.id(), request.tasteSummary());
		return CustomResponse.ok(Map.of("ok", true));
	}

}
