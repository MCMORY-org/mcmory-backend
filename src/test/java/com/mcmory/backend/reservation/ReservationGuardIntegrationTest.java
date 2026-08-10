package com.mcmory.backend.reservation;

import java.time.LocalDate;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예약 생성의 입력 경계와 권한 경계임. 2026-08-09 교차 검토에서 나온 결함들을 붙잡아 두는 것이 목적이고, 그 전까지 이 경로에는 자동화된 검증이
 * 하나도 없었음(수동 확인만 남아 있었음).
 *
 * 여기서 지키는 것은 문구가 아니라 **오류 코드**임 — 문구는 UX 사정으로 바뀌지만 코드는 프론트가 분기하는 계약임.
 *
 * 시드 전제: 회원 1(01012345678)이 보유 제품을 가지고 있고, 회원 2(01099998888)는 그 제품의 소유자가 아님.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationGuardIntegrationTest extends HttpIntegrationSupport {

	private static final String OWNER_PHONE = "01012345678";

	private static final String OTHER_PHONE = "01099998888";

	private static final String PASSWORD = "1234";

	@BeforeEach
	void 소유자로_로그인() {
		clearCookies();
		loginAs(OWNER_PHONE, PASSWORD);
	}

	@Test
	void 남의_보유_제품으로는_예약할_수_없다() {
		long ownedId = 내_보유_제품_id();

		// 소유자를 바꿔서 같은 제품 id로 예약을 건다
		clearCookies();
		loginAs(OTHER_PHONE, PASSWORD);
		Response response = 예약한다(1L, ownedId, 나흘_뒤(), "14:00", null);

		// store_reservation에 owned_product_id FK가 없어 DB가 못 막음. 서비스 검사가 유일한 방어선임
		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("RESV400_2");
	}

	@Test
	void 없는_보유_제품_id는_400이다() {
		Response response = 예약한다(1L, 999999L, 나흘_뒤(), "14:00", null);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("RESV400_2");
	}

	@Test
	void 매장_id가_빠지면_500이_아니라_400이다() {
		// storeId는 FK가 있지만 null은 NOT NULL 위반이라 무결성 catch가 갈라내지 못해 500으로 샜음
		Response response = 예약한다(null, 내_보유_제품_id(), 나흘_뒤(), "14:00", null);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("RESV400_2");
	}

	@Test
	void 지난_날짜로는_예약할_수_없다() {
		Response response = 예약한다(1L, 내_보유_제품_id(), LocalDate.now().minusDays(3).toString(), "14:00", null);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("RESV400_4");
	}

	@Test
	void 날짜_형식이_틀리면_500이_아니라_400이다() {
		Response response = 예약한다(1L, 내_보유_제품_id(), "2026-13-99", "14:00", null);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("VALID400_0");
	}

	@Test
	void 날짜가_빠지면_500이_아니라_400이다() {
		Response response = 예약한다(1L, 내_보유_제품_id(), null, "14:00", null);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("VALID400_2");
	}

	@Test
	void 요청사항이_길면_슬롯_충돌로_오역되지_않는다() {
		// 컬럼 길이 위반이 무결성 예외로 번역되면서 "방금 다른 분이 예약했어요"라는 거짓 안내가 나갔음
		Response response = 예약한다(1L, 내_보유_제품_id(), 나흘_뒤(), "15:00", "가".repeat(501));

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("RESV400_5");
	}

	@Test
	void 없는_매장의_슬롯은_비어_있다() {
		Response response = get("/api/v1/stores?storeId=999999&date=" + 나흘_뒤());

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.body().get("slots").isNull()).as(response.text()).isTrue();
	}

	@Test
	void 지난_날짜의_슬롯은_전부_PAST_사유로_비활성이다() {
		// 오늘 날짜로 보면 실행 시각에 따라 PAST가 하나도 없을 수 있고(첫 슬롯 10:00에 1시간 기준이라 오전 9시 전),
		// 그러면 조건부 단언이 한 번도 실행되지 않아 PAST 분기를 통째로 지워도 통과함. 어제로 고정해 전부를 단언함
		Response response = get("/api/v1/stores?storeId=1&date=" + LocalDate.now().minusDays(1));

		assertThat(response.status()).isEqualTo(200);
		var slots = response.body().get("slots");
		assertThat(slots.isNull()).as(response.text()).isFalse();
		// 개수를 상수에서 가져오지 않고 박아둠 — 상수를 늘리면 이 단언이 빨개져서 계약 문서와 화면을 함께 고치게 함
		assertThat(slots.size()).isEqualTo(10);

		for (var slot : slots) {
			assertThat(slot.get("state").asString()).as(slot.toString()).isEqualTo("DISABLED");
			assertThat(slot.get("reason").asString()).as(slot.toString()).isEqualTo("PAST");
		}
	}

	/**
	 * 테스트 시드에는 owned_product 행이 없으므로 데모 시리얼로 직접 등록함. 이미 등록돼 있으면 409가 오는데 그때는 목록에서 집어 옴 —
	 * 클래스 안 테스트들이 컨테이너를 공유해 실행 순서에 따라 둘 다 나올 수 있음.
	 */
	private long 내_보유_제품_id() {
		post("/api/v1/owned", "{\"serial\":\"MX2024A031\"}");

		Response owned = get("/api/v1/owned");
		assertThat(owned.status()).isEqualTo(200);
		assertThat(owned.body().get("list").size()).as(owned.text()).isGreaterThan(0);
		return owned.body().get("list").get(0).get("id").asLong();
	}

	private String 나흘_뒤() {
		return LocalDate.now().plusDays(4).toString();
	}

	private Response 예약한다(Long storeId, Long ownedProductId, String reserveDate, String timeSlot, String requestNote) {
		StringBuilder body = new StringBuilder("{");
		body.append("\"storeId\":").append(storeId);
		body.append(",\"ownedProductId\":").append(ownedProductId);
		body.append(",\"timeSlot\":\"").append(timeSlot).append("\"");
		if (reserveDate != null) {
			body.append(",\"reserveDate\":\"").append(reserveDate).append("\"");
		}
		if (requestNote != null) {
			body.append(",\"requestNote\":\"").append(requestNote).append("\"");
		}
		return post("/api/v1/reservations", body.append("}").toString());
	}

}
