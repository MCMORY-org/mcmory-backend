package com.mcmory.backend.reservation;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.mcmory.backend.global.apiPayload.code.ReservationErrorCode;
import com.mcmory.backend.global.apiPayload.code.ValidationErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.owned.OwnedProductRepository;
import com.mcmory.backend.product.Product;
import com.mcmory.backend.product.ProductRepository;
import com.mcmory.backend.store.StoreRepository;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

	/**
	 * ADR-012 슬롯 상수 10개임. 12시는 점심 휴게라 슬롯 자체가 없음 — 비활성이 아니라 부재임. 6개(16시까지)였던 것을 디자인 v1.1
	 * `CONTINUE-03-예약확정` 실측에 맞춰 20시까지 늘림 — 화면이 그리는 17시부터 20시가 서버에서 RESV400_1로 튕기던 것을 막음.
	 */
	public static final List<String> TIME_SLOTS = List.of("10:00", "11:00", "13:00", "14:00", "15:00", "16:00", "17:00",
			"18:00", "19:00", "20:00");

	/** FR-027: 방문 1시간 전까지만 앱에서 취소할 수 있음. 예약 가능 하한도 같은 값을 써서 두 규칙이 어긋나지 않게 함. */
	private static final Duration CANCEL_DEADLINE = Duration.ofHours(1);

	/** `store_reservation.request_note` 컬럼 길이임. 넘치면 DB가 무결성 오류를 내는데 그게 슬롯 충돌로 오역됨. */
	private static final int REQUEST_NOTE_MAX = 500;

	private final StoreReservationRepository reservations;

	private final StoreRepository stores;

	private final OwnedProductRepository owned;

	private final ProductRepository products;

	public ReservationService(StoreReservationRepository reservations, StoreRepository stores,
			OwnedProductRepository owned, ProductRepository products) {
		this.reservations = reservations;
		this.stores = stores;
		this.owned = owned;
		this.products = products;
	}

	public record ReservationView(
			@Schema(description = "예약 id. 취소 요청 본문의 `id`에 그대로 넣는 값임", example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED) Long id,
			@Schema(description = "방문 날짜 `YYYY-MM-DD`임", example = "2026-08-20",
					requiredMode = Schema.RequiredMode.REQUIRED) LocalDate reserveDate,
			@Schema(description = "방문 시각 `HH:mm`임. 슬롯 10개 중 하나이고 12시는 없음(점심 휴게)", example = "14:00",
					requiredMode = Schema.RequiredMode.REQUIRED) String timeSlot,
			@Schema(description = "요청 사항. 500자 이하이고 예약 때 비워둘 수 있어 선택임", example = "선물 포장 부탁드립니다", maxLength = 500,
					requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String requestNote,
			@Schema(description = "매장 이름. 함정 — 매장 id가 아니라 이름만 줌. 예약 뒤 매장이 지워지면 `null`임", example = "MCM 청담 플래그십",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String storeName,
			@Schema(description = "제품 이름. 함정 — 보유 제품 id가 아니라 이름만 줌. 보유 제품이나 제품이 지워지면 `null`임", example = "비세토스 백팩",
					requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String productName) {
	}

	@Transactional(readOnly = true)
	public List<ReservationView> list(Long memberId) {
		return this.reservations.findByMemberIdOrderByReserveDateAscTimeSlotAsc(memberId).stream().map((row) -> {
			String storeName = this.stores.findById(row.getStoreId()).map((store) -> store.getName()).orElse(null);
			String productName = this.owned.findById(row.getOwnedProductId())
				.map((item) -> item.getProductId())
				.flatMap(this.products::findById)
				.map(Product::getName)
				.orElse(null);

			return new ReservationView(row.getId(), row.getReserveDate(), row.getTimeSlot(), row.getRequestNote(),
					storeName, productName);
		}).toList();
	}

	/**
	 * FR-026 예약 생성임. 슬롯 선점 충돌은 DB 유니크 제약이 최종 방어선임(ADR-012) — 조회로 먼저 막으면 동시 두 요청이 둘 다 빈
	 * 슬롯을 보고 통과하므로 제약 위반을 409로 번역하는 경로가 반드시 있어야 함.
	 */
	@Transactional
	public Long create(Long memberId, Long storeId, Long ownedProductId, String reserveDate, String timeSlot,
			String requestNote) {
		if (!TIME_SLOTS.contains(timeSlot)) {
			throw new CustomException(ReservationErrorCode.INVALID_SLOT);
		}
		if (reserveDate == null || reserveDate.isBlank()) {
			throw new CustomException(ValidationErrorCode.MISSING_PARAMETER);
		}
		if (requestNote != null && requestNote.length() > REQUEST_NOTE_MAX) {
			throw new CustomException(ReservationErrorCode.NOTE_TOO_LONG);
		}

		// 형식이 틀린 날짜는 GlobalExceptionHandler가 400으로 번역함. 여기서 잡으면 아래 무결성 catch와 뒤섞임
		LocalDate date = LocalDate.parse(reserveDate);
		if (isPast(date, timeSlot)) {
			throw new CustomException(ReservationErrorCode.PAST_SLOT);
		}

		// 남의 제품이나 없는 제품으로는 예약할 수 없음. store_reservation에 owned_product_id FK가 없어
		// DB가 이걸 못 막으므로 여기가 유일한 방어선임.
		// storeId는 FK가 있지만 null은 FK 위반이 아니라 NOT NULL 위반이라 catch가 못 갈라내고 500이 됨 — 여기서 함께
		// 막음
		if (storeId == null || ownedProductId == null
				|| this.owned.findByIdAndMemberIdAndDeletedAtIsNull(ownedProductId, memberId).isEmpty()) {
			throw new CustomException(ReservationErrorCode.INVALID_REFERENCE);
		}

		try {
			StoreReservation saved = this.reservations.saveAndFlush(new StoreReservation(memberId, storeId,
					ownedProductId, date, timeSlot, (requestNote == null) ? "" : requestNote));
			return saved.getId();
		}
		catch (DataIntegrityViolationException ex) {
			// 유니크 위반(슬롯 선점)과 FK 위반(없는 매장)을 갈라줌. 둘을 한 문구로 합치면 사용자가 무엇을 고쳐야 하는지 알 수 없음.
			// 그 둘이 아닌 무결성 위반까지 슬롯 충돌로 번역하면 "다른 분이 예약했어요"가 거짓 안내가 되므로 그대로 올려보냄
			if (isForeignKeyViolation(ex)) {
				throw new CustomException(ReservationErrorCode.INVALID_REFERENCE);
			}
			if (isDuplicateEntry(ex)) {
				throw new CustomException(ReservationErrorCode.SLOT_TAKEN);
			}
			throw ex;
		}
	}

	/** FR-027 취소임. 취소는 행 삭제로 슬롯을 해제함(ADR-012). */
	@Transactional
	public void cancel(Long memberId, Long reservationId) {
		if (reservationId == null) {
			throw new CustomException(ReservationErrorCode.NOT_FOUND);
		}
		StoreReservation target = this.reservations.findByIdAndMemberId(reservationId, memberId)
			.orElseThrow(() -> new CustomException(ReservationErrorCode.NOT_FOUND));

		if (Duration.between(LocalDateTime.now(), target.visitAt()).compareTo(CANCEL_DEADLINE) < 0) {
			throw new CustomException(ReservationErrorCode.CANCEL_TOO_LATE);
		}

		this.reservations.delete(target);
	}

	/**
	 * ADR-012 결정 2의 PAST 판정임 — 이미 지났거나 1시간 이내로 임박한 슬롯. 취소 마감과 같은 기준을 써서 "예약은 되는데 바로 취소는 안
	 * 되는" 구간이 생기지 않게 함. 슬롯 문자열은 호출 전에 TIME_SLOTS로 검증되므로 파싱이 실패하지 않음.
	 */
	public static boolean isPast(LocalDate date, String timeSlot) {
		return LocalDateTime.of(date, LocalTime.parse(timeSlot)).isBefore(LocalDateTime.now().plus(CANCEL_DEADLINE));
	}

	private boolean isForeignKeyViolation(DataIntegrityViolationException ex) {
		return causeMessage(ex).contains("foreign key");
	}

	private boolean isDuplicateEntry(DataIntegrityViolationException ex) {
		return causeMessage(ex).contains("Duplicate entry");
	}

	private String causeMessage(DataIntegrityViolationException ex) {
		if (ex.getMostSpecificCause() == null || ex.getMostSpecificCause().getMessage() == null) {
			return "";
		}
		return ex.getMostSpecificCause().getMessage();
	}

}
