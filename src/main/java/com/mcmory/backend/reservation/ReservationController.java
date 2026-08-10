package com.mcmory.backend.reservation;

import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

	private final ReservationService reservations;

	private final CurrentMember currentMember;

	public ReservationController(ReservationService reservations, CurrentMember currentMember) {
		this.reservations = reservations;
		this.currentMember = currentMember;
	}

	public record CreateRequest(Long storeId, Long ownedProductId, String reserveDate, String timeSlot,
			String requestNote) {
	}

	public record IdRequest(Long id) {
	}

	@GetMapping
	public CustomResponse<Map<String, Object>> list() {
		List<ReservationService.ReservationView> list = this.reservations.list(this.currentMember.requireId());
		return CustomResponse.ok(Map.of("list", list));
	}

	@PostMapping
	public CustomResponse<Map<String, Object>> create(@RequestBody CreateRequest request) {
		Long id = this.reservations.create(this.currentMember.requireId(), request.storeId(), request.ownedProductId(),
				request.reserveDate(), request.timeSlot(), request.requestNote());
		return CustomResponse.ok(Map.of("ok", true, "id", id));
	}

	@DeleteMapping
	public CustomResponse<Map<String, Object>> cancel(@RequestBody IdRequest request) {
		this.reservations.cancel(this.currentMember.requireId(), request.id());
		return CustomResponse.ok(Map.of("ok", true));
	}

}
