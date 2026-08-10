package com.mcmory.backend.store;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.mcmory.backend.reservation.ReservationService;
import com.mcmory.backend.reservation.StoreReservation;
import com.mcmory.backend.reservation.StoreReservationRepository;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-025 매장 찾기임. 필터는 다중 선택 AND 결합이고 비회원도 볼 수 있음(로그인 요구 없음 — 프로토타입 라우트와 같음).
 *
 * storeId와 date를 함께 주면 슬롯 상태를 덧붙임. 화면이 매장 선택과 시간 선택을 한 흐름으로 처리해 호출을 나누지 않음.
 */
@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

	/**
	 * ponytail: 실제 거리 계산 대신 고정값임. 위치 권한과 좌표 계산은 승격 때 넣음 — 프로토타입이 검증할 대상은 정렬과 필터의 결합이지 거리
	 * 정확도가 아님.
	 */
	private static final Map<Long, Double> FIXED_DISTANCE_KM = Map.of(1L, 1.2, 2L, 2.8, 3L, 4.1, 4L, 7.4);

	private final StoreRepository stores;

	private final StoreReservationRepository reservations;

	public StoreController(StoreRepository stores, StoreReservationRepository reservations) {
		this.stores = stores;
		this.reservations = reservations;
	}

	public record StoreView(Long id, String name, String address, double distanceKm, String closeTime,
			boolean repairAvailable, boolean openNow, boolean reservable) {
	}

	public record SlotView(String slot, String state, String reason) {
	}

	/** slots는 storeId와 date가 함께 오지 않으면 null임. 프로토타입 응답 형태를 그대로 유지함. */
	public record StoresResponse(List<StoreView> list, List<SlotView> slots) {
	}

	@GetMapping
	@Transactional(readOnly = true)
	public CustomResponse<StoresResponse> stores(@RequestParam(required = false) String repair,
			@RequestParam(required = false) String openNow, @RequestParam(required = false) String reservable,
			@RequestParam(required = false) Long storeId, @RequestParam(required = false) String date) {

		boolean onlyRepair = "1".equals(repair);
		boolean onlyOpenNow = "1".equals(openNow);
		boolean onlyReservable = "1".equals(reservable);
		LocalTime now = LocalTime.now();

		List<StoreView> list = this.stores.findAllByOrderById()
			.stream()
			.map((store) -> new StoreView(store.getId(), store.getName(), store.getAddress(),
					FIXED_DISTANCE_KM.getOrDefault(store.getId(), 9.9), store.getCloseTime().toString(),
					store.isRepairAvailable(), !now.isBefore(store.getOpenTime()) && !now.isAfter(store.getCloseTime()),
					isReservable(store)))
			.filter((view) -> (!onlyRepair || view.repairAvailable()) && (!onlyOpenNow || view.openNow())
					&& (!onlyReservable || view.reservable()))
			.sorted((left, right) -> Double.compare(left.distanceKm(), right.distanceKm()))
			.toList();

		return CustomResponse.ok(new StoresResponse(list, slotsOf(storeId, date)));
	}

	/**
	 * 예약 가능은 수리 가능 매장 중 서비스센터를 제외한다는 규칙으로 근사함. 실제 운영 규칙은 매장 데이터에 플래그가 생겨야 함.
	 *
	 * 이전 구현은 id 2를 제외했는데 시드의 id 2는 갤러리아이고 서비스센터는 id 3이라 주석과 동작이 어긋나 있었음 — id 상수 대신 이름으로
	 * 판정해 규칙과 코드가 같은 말을 하게 함.
	 */
	private boolean isReservable(Store store) {
		return store.isRepairAvailable() && !store.getName().contains("서비스센터");
	}

	/**
	 * 없는 매장은 파라미터가 없을 때와 같이 slots를 비움 — 이 경로는 비회원 공개라 없는 id로 6칸 전부 예약 가능처럼 보이면 안 됨. 에러 대신
	 * null인 이유는 정상 프론트가 방금 받은 목록의 id만 보내기 때문임.
	 */
	private List<SlotView> slotsOf(Long storeId, String date) {
		if (storeId == null || date == null || date.isBlank() || !this.stores.existsById(storeId)) {
			return null;
		}

		LocalDate target = LocalDate.parse(date);
		Set<String> taken = this.reservations.findByStoreIdAndReserveDate(storeId, target)
			.stream()
			.map(StoreReservation::getTimeSlot)
			.collect(Collectors.toSet());

		// ADR-012 슬롯 상태와 사유 코드임. 사유는 화면이 "이미 예약됨"과 "지난 시각"을 구분하는 근거이고,
		// state는 프론트가 유니언 두 값으로만 읽으므로 PAST를 새 state로 만들지 않고 DISABLED의 사유로 둠
		return ReservationService.TIME_SLOTS.stream().map((slot) -> {
			if (taken.contains(slot)) {
				return new SlotView(slot, "DISABLED", "BOOKED");
			}
			if (ReservationService.isPast(target, slot)) {
				return new SlotView(slot, "DISABLED", "PAST");
			}
			return new SlotView(slot, "AVAILABLE", null);
		}).toList();
	}

}
