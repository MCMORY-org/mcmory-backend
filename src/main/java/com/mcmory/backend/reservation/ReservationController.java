package com.mcmory.backend.reservation;

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
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "예약",
		description = "매장 예약 조회·생성·취소. 명세서 5.7절(#23, #24, #25)이 계약 정본임. 전 엔드포인트 인증 필수이며 미인증은 AUTH401_1 반환. 절 전체가 이번 제출 `범위밖`이라 화면은 만들지 않으나 계약과 구현은 그대로 유지함(ADR-012 개정 이력 2026-08-11). 슬롯은 10개 고정(`10:00`, `11:00`, `13:00`부터 `20:00`까지 매시)이고 12시는 비활성이 아니라 아예 없음(점심 휴게).")
@SecurityRequirement(name = OpenApiConfig.ACCESS_COOKIE)
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

	private final ReservationService reservations;

	private final CurrentMember currentMember;

	public ReservationController(ReservationService reservations, CurrentMember currentMember) {
		this.reservations = reservations;
		this.currentMember = currentMember;
	}

	public record CreateRequest(@Schema(
			description = "예약할 매장 id. `GET /api/v1/stores`의 `result.list[].id`를 그대로 씀. 없는 매장이거나 누락이면 `RESV400_2`임",
			example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long storeId,
			@Schema(description = "방문할 보유 제품 id. 함정 — **내 것이고 삭제되지 않은 보유 제품만** 허용함. 남의 것이거나 없거나 누락이면 존재 여부를 알려주지 않고 모두 `RESV400_2`임. 제품 id가 아니라 보유 제품 id임",
					example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long ownedProductId,
			@Schema(description = "방문 날짜 `YYYY-MM-DD`임. 형식이 틀리면 `VALID400_0`, 누락이면 `VALID400_2`임",
					example = "2026-08-20", pattern = "^\\d{4}-\\d{2}-\\d{2}$",
					requiredMode = Schema.RequiredMode.REQUIRED) String reserveDate,
			@Schema(description = "방문 시각 `HH:mm`임. 슬롯 10개 고정(`10:00`, `11:00`, `13:00`부터 `20:00`까지 매시)이고 **12시는 아예 없음**(점심 휴게). 밖이면 `RESV400_1`임. 함정 — `reserveDate`와 합친 방문 시각이 현재로부터 1시간 이내면 슬롯 목록에 있어도 `RESV400_4`로 거부함",
					example = "14:00", allowableValues = {
							"10:00", "11:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00" },
					requiredMode = Schema.RequiredMode.REQUIRED) String timeSlot,
			@Schema(description = "요청 사항. **500자 이하**이고 초과는 `RESV400_5`임. 매장에 남길 말이 없을 수 있어 선택이며, 생략하거나 `null`이면 빈 문자열로 저장함",
					example = "선물 포장 부탁드립니다", maxLength = 500, requiredMode = Schema.RequiredMode.NOT_REQUIRED,
					nullable = true) String requestNote){
	}

	public record IdRequest(@Schema(
			description = "취소할 예약 id. `GET /api/v1/reservations`의 `result.list[].id`임. 함정 — **남의 예약과 없는 예약이 같은 `RESV404_1`이라** 존재 여부를 알 수 없음",
			example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long id) {
	}

	@Operation(summary = "내 예약 목록 조회",
			description = "로그인한 회원 본인의 예약 목록을 반환함(명세서 5.7 #23). 인증 필수 — 세션이 없으면 `AUTH401_1`임. `result.list` 원소 필드는 `id`, `reserveDate`, `timeSlot`, `requestNote`, `storeName`, `productName` 여섯이며 `requestNote`는 null일 수 있음. 페이지네이션 없음.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "성공", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": {
									    "list": [
									      {
									        "id": 1,
									        "reserveDate": "2026-08-20",
									        "timeSlot": "14:00",
									        "requestNote": null,
									        "storeName": "MCM 청담 플래그십",
									        "productName": "비세토스 백팩"
									      }
									    ]
									  }
									}
									"""))),
			@ApiResponse(responseCode = "401", description = "미인증",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1 로그인이 필요합니다", value = """
									{
									  "isSuccess": false,
									  "code": "AUTH401_1",
									  "message": "로그인이 필요합니다",
									  "result": null
									}
									"""))) })
	@GetMapping
	public CustomResponse<Map<String, Object>> list() {
		List<ReservationService.ReservationView> list = this.reservations.list(this.currentMember.requireId());
		return CustomResponse.ok(Map.of("list", list));
	}

	@Operation(summary = "매장 예약 생성",
			description = "매장과 보유 제품과 방문 일시로 예약을 만듦(명세서 5.7 #24). 인증 필수. 함정 셋 — (1) `ownedProductId`는 **내 것이고 삭제되지 않은 보유 제품만** 허용하며 남의 것이거나 누락이면 `RESV400_2`임, (2) 방문 시각이 현재로부터 1시간 이내면 `RESV400_4`로 거부함, (3) 같은 슬롯 동시 요청은 DB 유니크 제약이 한 명만 통과시켜 진 쪽이 `RESV409_1`을 받는데 **이는 오류가 아니라 정상 경로**이므로 다른 시간을 고르게 안내할 것. 자원을 만들지만 201이 아니라 200으로 응답함(명세서 6장).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "예약 성공",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "성공", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": {
									    "ok": true,
									    "id": 1
									  }
									}
									"""))),
			@ApiResponse(responseCode = "400", description = "요청 값 오류. 문구가 아니라 `code`로 분기할 것",
					content = @Content(mediaType = "application/json",
							examples = { @ExampleObject(name = "RESV400_1 슬롯 밖 시간", value = """
									{
									  "isSuccess": false,
									  "code": "RESV400_1",
									  "message": "선택할 수 없는 시간입니다. 다른 시간을 골라주세요",
									  "result": null
									}
									"""), @ExampleObject(name = "RESV400_2 매장·보유 제품 오류", value = """
									{
									  "isSuccess": false,
									  "code": "RESV400_2",
									  "message": "선택한 제품이나 매장 정보를 확인해주세요",
									  "result": null
									}
									"""), @ExampleObject(name = "RESV400_4 임박·과거 시각", value = """
									{
									  "isSuccess": false,
									  "code": "RESV400_4",
									  "message": "이미 지났거나 곧 시작하는 시간입니다. 다른 시간을 골라주세요",
									  "result": null
									}
									"""), @ExampleObject(name = "RESV400_5 요청 사항 500자 초과", value = """
									{
									  "isSuccess": false,
									  "code": "RESV400_5",
									  "message": "요청 사항은 500자까지 쓸 수 있습니다",
									  "result": null
									}
									"""), @ExampleObject(name = "VALID400_0 날짜 형식 오류", value = """
									{
									  "isSuccess": false,
									  "code": "VALID400_0",
									  "message": "요청 값의 형식을 확인해주세요",
									  "result": null
									}
									"""), @ExampleObject(name = "VALID400_2 reserveDate 누락", value = """
									{
									  "isSuccess": false,
									  "code": "VALID400_2",
									  "message": "필수 입력 항목을 모두 작성해주세요",
									  "result": null
									}
									""") })),
			@ApiResponse(responseCode = "401", description = "미인증",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1 로그인이 필요합니다", value = """
									{
									  "isSuccess": false,
									  "code": "AUTH401_1",
									  "message": "로그인이 필요합니다",
									  "result": null
									}
									"""))),
			@ApiResponse(responseCode = "409", description = "슬롯 선점 충돌. 오류가 아니라 정상 경로임",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "RESV409_1 동시 예약", value = """
									{
									  "isSuccess": false,
									  "code": "RESV409_1",
									  "message": "방금 다른 분이 예약했습니다. 다른 시간을 골라주세요",
									  "result": null
									}
									"""))) })
	@PostMapping
	public CustomResponse<Map<String, Object>> create(@RequestBody CreateRequest request) {
		Long id = this.reservations.create(this.currentMember.requireId(), request.storeId(), request.ownedProductId(),
				request.reserveDate(), request.timeSlot(), request.requestNote());
		return CustomResponse.ok(Map.of("ok", true, "id", id));
	}

	@Operation(summary = "예약 취소",
			description = "본인 예약을 취소함(명세서 5.7 #25). 인증 필수이고 요청 본문은 `{ \"id\": number }` 하나임. 함정 둘 — (1) **남의 예약과 없는 예약이 같은 `RESV404_1`임**(존재 여부를 알려주지 않음), (2) 방문 1시간 이내는 앱에서 취소할 수 없어 `RESV400_3`이며 매장 연락으로 안내할 것.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "취소 성공",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "성공", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": {
									    "ok": true
									  }
									}
									"""))),
			@ApiResponse(responseCode = "400", description = "취소 마감 경과",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "RESV400_3 취소 마감", value = """
									{
									  "isSuccess": false,
									  "code": "RESV400_3",
									  "message": "방문 1시간 이내에는 앱에서 취소할 수 없습니다. 매장으로 연락해주세요",
									  "result": null
									}
									"""))),
			@ApiResponse(responseCode = "401", description = "미인증",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1 로그인이 필요합니다", value = """
									{
									  "isSuccess": false,
									  "code": "AUTH401_1",
									  "message": "로그인이 필요합니다",
									  "result": null
									}
									"""))),
			@ApiResponse(responseCode = "404", description = "없는 예약 또는 남의 예약",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "RESV404_1 예약 없음", value = """
									{
									  "isSuccess": false,
									  "code": "RESV404_1",
									  "message": "예약 정보를 찾을 수 없습니다",
									  "result": null
									}
									"""))) })
	@DeleteMapping
	public CustomResponse<Map<String, Object>> cancel(@RequestBody IdRequest request) {
		this.reservations.cancel(this.currentMember.requireId(), request.id());
		return CustomResponse.ok(Map.of("ok", true));
	}

}
