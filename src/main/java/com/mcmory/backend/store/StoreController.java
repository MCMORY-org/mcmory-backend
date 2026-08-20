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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "매장", description = "매장을 찾고 예약 가능한 슬롯을 조회함.")
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

	public record StoreView(
			@Schema(description = "매장 id. 예약 생성(`POST /api/v1/reservations`)의 `storeId`와 슬롯 조회 쿼리 `storeId`에 그대로 넣는 값임",
					example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
			@Schema(description = "매장 이름", example = "MCM 강남 본점",
					requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "매장 주소", example = "서울 강남구 압구정로",
					requiredMode = Schema.RequiredMode.REQUIRED) String address,
			@Schema(description = "거리. **km 단위**임. 함정 — 실제 좌표 거리 계산이 아니라 프로토타입 고정값이고, 목록 정렬은 이 값의 오름차순 고정임",
					example = "1.2", requiredMode = Schema.RequiredMode.REQUIRED) double distanceKm,
			@Schema(description = "마감 시각 `HH:mm`임", example = "20:00",
					requiredMode = Schema.RequiredMode.REQUIRED) String closeTime,
			@Schema(description = "수리 가능 여부. 함정 — 매장의 **전역** 플래그이고 제품별 수리 카테고리 매칭은 구현하지 않았음. 쿼리 `repair=1`이 보는 값이 이것임",
					example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean repairAvailable,
			@Schema(description = "조회 시각이 매장 영업 시간 안인지 여부임. 요청 시점마다 달라지는 계산값임", example = "true",
					requiredMode = Schema.RequiredMode.REQUIRED) boolean openNow,
			@Schema(description = "예약 가능 여부. 함정 — 별도 컬럼이 아니라 `repairAvailable`이 참이면서 이름에 `서비스센터`가 없는 매장이라는 근사 규칙임",
					example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean reservable) {
	}

	public record SlotView(@Schema(
			description = "슬롯 시각 `HH:mm`임. 10개 고정(`10:00`, `11:00`, `13:00`부터 `20:00`까지 매시)이고 **12시는 비활성이 아니라 아예 없음**(점심 휴게). 예약 생성(`POST /api/v1/reservations`)의 `timeSlot`에 그대로 넣는 값임",
			example = "13:00", requiredMode = Schema.RequiredMode.REQUIRED) String slot,
			@Schema(description = "슬롯 상태. `AVAILABLE`과 `DISABLED` **두 값뿐임**. 함정 — `PAST`는 state가 아니라 `reason`임",
					example = "AVAILABLE", allowableValues = {
							"AVAILABLE", "DISABLED" },
					requiredMode = Schema.RequiredMode.REQUIRED) String state,
			@Schema(description = "비활성 사유. `BOOKED`(이미 예약됨)와 `PAST`(이미 지났거나 1시간 이내로 임박) 둘임. `state`가 `AVAILABLE`이면 항상 `null`이라 선택임",
					example = "BOOKED", allowableValues = { "BOOKED", "PAST" },
					requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String reason){
	}

	/** slots는 storeId와 date가 함께 오지 않으면 null임. 프로토타입 응답 형태를 그대로 유지함. */
	public record StoresResponse(
			@Schema(description = "필터를 통과한 매장 목록임. 거리 오름차순 고정이고, 조건에 맞는 매장이 없으면 빈 배열임",
					requiredMode = Schema.RequiredMode.REQUIRED) List<StoreView> list,
			@Schema(description = "슬롯 목록. 함정 — `storeId`와 `date`를 **함께** 준 경우에만 채워지고, 둘 중 하나라도 없거나 없는 `storeId`면 에러가 아니라 `null`임. 채워지면 항상 10개임",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) List<SlotView> slots) {
	}

	@Operation(summary = "매장 목록과 예약 슬롯 조회",
			description = """
					**인증 불필요**(비회원 공개 경로).

					필터 `repair`·`openNow`·`reservable`은 값이 `"1"`일 때만 켜지고 전부 AND로 결합함. 정렬은 거리 오름차순 고정임.

					**함정 1**: `storeId`와 `date`를 **함께** 줘야 `result.slots`가 채워짐. 둘 중 하나라도 없으면 `slots`는 `null`임.

					**함정 2**: 없는 `storeId`를 줘도 에러가 아니라 `slots`가 `null`임 — 빈 결과로 응답함.

					**함정 3**: 슬롯은 10개 고정(`10:00`, `11:00`, `13:00`부터 `20:00`까지 매시)이고 **12시는 비활성이 아니라 아예 없음**(점심 휴게).

					**함정 4**: `state`는 `AVAILABLE`과 `DISABLED` 두 값뿐임. `PAST`는 state가 아니라 `DISABLED`의 `reason`임(다른 사유는 `BOOKED`). `AVAILABLE`이면 `reason`은 `null`임.

					**함정 5**: `repair=1`은 매장의 전역 `repairAvailable` 플래그만 봄. **제품별 수리 카테고리 매칭은 구현하지 않았음**.

					**함정 6**: `distanceKm`은 실제 거리 계산이 아니라 프로토타입 고정값임.

					`storeId`를 빼거나 `date`를 빼면 에러 대신 `slots`가 `null`임. 없는 매장 id도 같음. **둘을 다 보냈는데 `date`가 `yyyy-MM-dd`가 아니면** 파싱에 실패해 `COMMON400`이 나감.""")
	@ApiResponse(responseCode = "200",
			description = "성공. 봉투 `code`는 문자열 `\"200\"`임. `slots`는 `storeId`와 `date`를 함께 준 경우에만 채워지고 그 외에는 `null`임",
			content = @Content(mediaType = "application/json", examples = {
					@ExampleObject(name = "목록만 (storeId·date 미지정)",
							description = "`GET /api/v1/stores`의 `slots`는 `null` 규칙", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": {
									    "list": [
									      {
									        "id": 1,
									        "name": "MCM 강남 본점",
									        "address": "서울 강남구 압구정로",
									        "distanceKm": 1.2,
									        "closeTime": "20:00",
									        "repairAvailable": true,
									        "openNow": true,
									        "reservable": true
									      }
									    ],
									    "slots": null
									  }
									}"""),
					@ExampleObject(name = "슬롯 포함 (storeId·date 동시 지정)",
							description = "`GET /api/v1/stores`의 슬롯 상태 규칙 — state는 두 값뿐이고 PAST는 reason임", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": {
									    "list": [
									      {
									        "id": 1,
									        "name": "MCM 강남 본점",
									        "address": "서울 강남구 압구정로",
									        "distanceKm": 1.2,
									        "closeTime": "20:00",
									        "repairAvailable": true,
									        "openNow": true,
									        "reservable": true
									      }
									    ],
									    "slots": [
									      { "slot": "10:00", "state": "DISABLED", "reason": "PAST" },
									      { "slot": "11:00", "state": "DISABLED", "reason": "BOOKED" },
									      { "slot": "13:00", "state": "AVAILABLE", "reason": null }
									    ]
									  }
									}""") }))
	@GetMapping
	@Transactional(readOnly = true)
	public CustomResponse<StoresResponse> stores(
			@Parameter(description = "수리 가능 매장만. 값이 `\"1\"`일 때만 적용됨",
					example = "1") @RequestParam(required = false) String repair,
			@Parameter(description = "지금 영업 중인 매장만. 값이 `\"1\"`일 때만 적용됨",
					example = "1") @RequestParam(required = false) String openNow,
			@Parameter(description = "예약 가능 매장만. 값이 `\"1\"`일 때만 적용됨",
					example = "1") @RequestParam(required = false) String reservable,
			@Parameter(description = "슬롯을 조회할 매장 id. `date`와 함께 줘야 함. 없는 id면 에러 대신 `slots`가 `null`임",
					example = "1") @RequestParam(required = false) Long storeId,
			@Parameter(description = "슬롯 조회 날짜 `YYYY-MM-DD`. `storeId`와 함께 줘야 함",
					example = "2026-08-21") @RequestParam(required = false) String date) {

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
