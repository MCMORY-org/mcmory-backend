package com.mcmory.backend.owned;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;
import com.mcmory.backend.global.apiPayload.code.OwnedProductErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.product.Product;
import com.mcmory.backend.product.ProductRepository;
import com.mcmory.backend.styling.StylingService;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "보유 제품",
		description = "로그인 사용자의 보유 제품 목록과 데모 시리얼 등록, 삭제, 카테고리별 관리 방법임(API명세서 5.6). 모든 엔드포인트가 인증 필수이며 비로그인은 `AUTH401_1`임. 응답은 표준 봉투 `{isSuccess, code, message, result}`이고 성공 `code`는 문자열 \"200\"임.")
@RestController
@RequestMapping("/api/v1/owned")
public class OwnedProductController {

	private final OwnedProductRepository owned;

	private final ProductRepository products;

	private final CurrentMember currentMember;

	private final StylingService styling;

	public OwnedProductController(OwnedProductRepository owned, ProductRepository products, CurrentMember currentMember,
			StylingService styling) {
		this.owned = owned;
		this.products = products;
		this.currentMember = currentMember;
		this.styling = styling;
	}

	@Schema(name = "OwnedRegisterRequest", description = "데모 시리얼로 보유 제품을 등록하는 요청임(20번).")
	public record RegisterRequest(@Schema(
			description = "시드 카탈로그의 데모 일련번호임. **대소문자 무시 완전 일치만 등록됨** — 부분 일치는 지원하지 않음. 서버가 trim 후 대문자로 정규화해 저장함. 시연용 더미 데이터 조회이며 정품 인증이 아님(ADR-004, NFR-004). 빈 값이나 공백만이면 `OWNED400_1`, 일치하는 시드 제품이 없으면 `OWNED404_1`, 같은 상품을 `EXTERNAL` 출처로 이미 등록했으면 `OWNED409_1`임",
			example = "MX2024A031", requiredMode = Schema.RequiredMode.REQUIRED) String serial) {
	}

	@Schema(name = "OwnedIdRequest",
			description = "보유 제품 삭제 요청임(21번). **경로가 아니라 요청 본문으로 id를 받음** — DELETE에 본문이 필요하므로 클라이언트 설정에 주의할 것.")
	public record IdRequest(@Schema(
			description = "삭제할 보유 제품 id임(19번 목록의 `id`). 누락, 없는 id, 남의 제품, 이미 삭제된 제품은 존재 여부를 노출하지 않도록 모두 `OWNED404_2`로 동일하게 응답함",
			example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long id) {
	}

	@Schema(name = "OwnedProductView", description = "보유 제품에 연결된 상품의 표시용 요약임.")
	public record ProductView(
			@Schema(description = "상품 id임. 화면은 이 값으로 상품 이미지를 고름", example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED) Long productId,
			@Schema(description = "상품명임", example = "Tracy 비세토스 크로스바디",
					requiredMode = Schema.RequiredMode.REQUIRED) String name) {
	}

	@Schema(name = "OwnedView", description = "보유 제품 목록의 한 행임(19번).")
	public record OwnedView(
			@Schema(description = "보유 제품 id임. 21번 삭제와 32번 관리 방법 조회에 그대로 씀", example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED) Long id,
			@Schema(description = "등록 출처임. `GIFT`(우리 서비스로 받은 선물)와 `EXTERNAL`(사용자가 데모 시리얼로 직접 등록) **2종**임",
					example = "EXTERNAL", allowableValues = {
							"GIFT", "EXTERNAL" },
					requiredMode = Schema.RequiredMode.REQUIRED) String source,
			@Schema(description = "등록에 쓴 일련번호 메모임. **검증값이 아니라 메모임**(ADR-004) — 정품 여부를 뜻하지 않음. `GIFT` 출처는 시리얼이 없어 `null`임",
					example = "MX2024A031", nullable = true,
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) String serialMemo,
			@Schema(description = "등록 일자임. **시각이 아니라 날짜(`yyyy-MM-dd`)임**", example = "2026-08-11", type = "string",
					format = "date", nullable = true,
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate createdAt,
			@Schema(description = "연결된 상품 요약임. **상품 연결이 없거나 상품이 삭제된 경우 `null`이라 프론트에서 null 방어가 필요함**", nullable = true,
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) ProductView product){
	}

	@Operation(summary = "보유 제품 목록 (#19)",
			description = "로그인 사용자의 삭제되지 않은 보유 제품을 id 오름차순으로 반환함. 인증 필수임. `product`는 상품 연결이 없거나 상품이 삭제된 경우 `null`일 수 있으므로 프론트에서 null 방어가 필요함. `createdAt`은 시각이 아니라 날짜(`yyyy-MM-dd`)임.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "목록 반환",
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
									        "source": "EXTERNAL",
									        "serialMemo": "MCM-2024-0001",
									        "createdAt": "2026-08-11",
									        "product": { "productId": 2, "name": "비세토스 토트백" }
									      }
									    ]
									  }
									}
									"""))),
			@ApiResponse(responseCode = "401", description = "비로그인",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1", value = """
									{ "isSuccess": false, "code": "AUTH401_1", "message": "로그인이 필요합니다", "result": null }
									"""))) })
	@GetMapping
	@Transactional(readOnly = true)
	public CustomResponse<Map<String, Object>> list() {
		Long memberId = this.currentMember.requireId();

		List<OwnedView> list = this.owned.findByMemberIdAndDeletedAtIsNullOrderById(memberId).stream().map((row) -> {
			Product product = (row.getProductId() == null) ? null
					: this.products.findById(row.getProductId()).orElse(null);

			return new OwnedView(row.getId(), row.getSource(), row.getSerialMemo(),
					(row.getCreatedAt() == null) ? null : row.getCreatedAt().toLocalDate(),
					(product == null) ? null : new ProductView(product.getId(), product.getName()));
		}).toList();

		return CustomResponse.ok(Map.of("list", list));
	}

	/**
	 * FR-028 데모 시리얼 매칭 등록임. 대소문자 무시 완전 일치만 하고 불일치는 안내만 하며 등록하지 않음.
	 *
	 * 실패 문구에 인증이나 정품 표현을 쓰지 않음(ADR-004, NFR-004) — 조회 출처가 없어 정품 여부를 말할 근거가 없음.
	 */
	@Operation(summary = "데모 시리얼로 보유 제품 등록 (#20)",
			description = "인증 필수임. 시드 카탈로그의 데모 일련번호와 **대소문자 무시 완전 일치**만 등록됨 — 부분 일치를 허용하면 없는 번호가 통과하므로 지원하지 않음. 입력은 trim 후 대문자로 정규화해 저장함. **시연용 더미 데이터 조회이며 정품 인증이 아님**(ADR-004, NFR-004) — 화면에 인증이나 정품 표현을 쓰지 말 것. 같은 상품을 `EXTERNAL` 출처로 이미 등록했으면 `OWNED409_1`임.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "등록 성공",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "성공", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": {
									    "ok": true,
									    "product": { "productId": 2, "name": "비세토스 토트백" }
									  }
									}
									"""))),
			@ApiResponse(responseCode = "400", description = "일련번호 누락", content = @Content(
					mediaType = "application/json", examples = @ExampleObject(name = "OWNED400_1", value = """
							{ "isSuccess": false, "code": "OWNED400_1", "message": "일련번호를 입력해주세요", "result": null }
							"""))),
			@ApiResponse(responseCode = "401", description = "비로그인",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1", value = """
									{ "isSuccess": false, "code": "AUTH401_1", "message": "로그인이 필요합니다", "result": null }
									"""))),
			@ApiResponse(responseCode = "404", description = "일치하는 시드 제품 없음",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "OWNED404_1",
									value = """
											{ "isSuccess": false, "code": "OWNED404_1", "message": "등록된 제품 정보를 찾지 못했습니다. 제품 태그와 보증서의 일련번호를 다시 확인해주세요", "result": null }
											"""))),
			@ApiResponse(responseCode = "409", description = "이미 등록한 제품", content = @Content(
					mediaType = "application/json", examples = @ExampleObject(name = "OWNED409_1", value = """
							{ "isSuccess": false, "code": "OWNED409_1", "message": "이미 등록된 제품입니다", "result": null }
							"""))) })
	@PostMapping
	@Transactional
	public CustomResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) {
		Long memberId = this.currentMember.requireId();

		String serial = (request.serial() == null) ? "" : request.serial().trim().toUpperCase();
		if (serial.isEmpty()) {
			throw new CustomException(OwnedProductErrorCode.SERIAL_REQUIRED);
		}

		Product product = this.products.findByDemoSerialIgnoringCase(serial)
			.orElseThrow(() -> new CustomException(OwnedProductErrorCode.SERIAL_NOT_MATCHED));

		this.owned.findByMemberIdAndProductIdAndSourceAndDeletedAtIsNull(memberId, product.getId(), "EXTERNAL")
			.ifPresent((existing) -> {
				throw new CustomException(OwnedProductErrorCode.DUPLICATE);
			});

		this.owned.save(OwnedProduct.external(memberId, product.getId(), serial));
		return CustomResponse.ok(Map.of("ok", true, "product", new ProductView(product.getId(), product.getName())));
	}

	/**
	 * FR-024 관리 방법임. **시연용 일반 안내이지 MCM 공식 관리 지침이 아님**(NFR-004) — 출처가 없는 문구를 공식으로 표기하면 안
	 * 됨. 프론트도 그렇게 표기할 것.
	 *
	 * 테이블을 만들지 않은 이유는 시드 카테고리가 셋뿐이라서임. 카테고리가 늘거나 문구를 운영자가 고쳐야 하는 순간이 오면 그때 테이블로 옮김.
	 */
	private static final Map<String, List<String>> CARE_GUIDE = Map.of("가방",
			List.of("쓰고 난 뒤 부드러운 마른 천으로 표면을 닦아주세요", "직사광선과 습기를 피해 보관해주세요", "형태가 눌리지 않게 안쪽에 완충재를 넣어주세요"), "지갑",
			List.of("카드와 영수증을 가득 채우면 모양이 늘어납니다", "물에 젖으면 문지르지 말고 눌러서 물기를 빼주세요", "가죽 전용 크림을 얇게 발라주세요"), "가죽 소품",
			List.of("마찰이 잦은 모서리는 특히 조심해주세요", "다른 가죽 제품과 겹쳐 두면 색이 옮을 수 있습니다", "보관할 때는 개별 파우치에 넣어주세요"));

	private static final List<String> CARE_GUIDE_DEFAULT = List.of("직사광선과 습기를 피해 보관해주세요", "오염은 마른 천으로 가볍게 닦아주세요",
			"심한 오염이나 손상은 매장에 문의해주세요");

	@Schema(name = "OwnedCareGuide",
			description = "카테고리별 관리 방법 응답임(32번). **시연용 일반 안내이지 MCM 공식 관리 지침이 아님**(NFR-004) — 화면에도 그렇게 표기할 것.")
	public record CareGuide(
			@Schema(description = "조회한 보유 제품 id임(경로 변수 `id`와 같음)", example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED) Long ownedProductId,
			@Schema(description = "연결된 상품명임", example = "Tracy 비세토스 크로스바디",
					requiredMode = Schema.RequiredMode.REQUIRED) String productName,
			@Schema(description = "상품 카테고리임. 시드 기준 `가방`·`지갑`·`가죽 소품` **3종**이고, 그 밖의 값은 오류 대신 공통 기본 안내를 줌. **상품 카테고리이지 취향 색상이나 가방 디자인 축이 아님**",
					example = "가방", requiredMode = Schema.RequiredMode.REQUIRED) String category,
			@Schema(description = "관리 안내 문구 목록임. 카테고리별 3문장, 그 밖의 카테고리는 공통 기본 3문장임",
					example = "[\"쓰고 난 뒤 부드러운 마른 천으로 표면을 닦아주세요\"]",
					requiredMode = Schema.RequiredMode.REQUIRED) List<String> items) {
	}

	@Operation(summary = "카테고리별 관리 방법 (#32)",
			description = "인증 필수임. 보유 제품의 상품 카테고리로 관리 안내 문구를 반환함. 카테고리는 시드 기준 `가방`·`지갑`·`가죽 소품` 셋이고 그 밖은 오류 대신 공통 기본 안내를 줌. **시연용 일반 안내이지 MCM 공식 관리 지침이 아님**(NFR-004) — 화면에도 그렇게 표기할 것. 없거나 남의 제품, 삭제된 제품, 상품 연결이 끊긴 제품은 모두 `OWNED404_2`임. 화면 미구현 상태의 엔드포인트임(명세서 5.0).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "관리 방법 반환",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "성공", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": {
									    "ownedProductId": 1,
									    "productName": "비세토스 토트백",
									    "category": "가방",
									    "items": [
									      "쓰고 난 뒤 부드러운 마른 천으로 표면을 닦아주세요",
									      "직사광선과 습기를 피해 보관해주세요",
									      "형태가 눌리지 않게 안쪽에 완충재를 넣어주세요"
									    ]
									  }
									}
									"""))),
			@ApiResponse(responseCode = "401", description = "비로그인",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1", value = """
									{ "isSuccess": false, "code": "AUTH401_1", "message": "로그인이 필요합니다", "result": null }
									"""))),
			@ApiResponse(responseCode = "404", description = "없거나 남의 제품, 삭제된 제품", content = @Content(
					mediaType = "application/json", examples = @ExampleObject(name = "OWNED404_2", value = """
							{ "isSuccess": false, "code": "OWNED404_2", "message": "제품 정보를 찾을 수 없습니다", "result": null }
							"""))) })
	@GetMapping("/{id}/care-guide")
	@Transactional(readOnly = true)
	public CustomResponse<CareGuide> careGuide(@PathVariable Long id) {
		Long memberId = this.currentMember.requireId();

		if (id == null) {
			throw new CustomException(OwnedProductErrorCode.NOT_FOUND);
		}
		OwnedProduct target = this.owned.findByIdAndMemberIdAndDeletedAtIsNull(id, memberId)
			.orElseThrow(() -> new CustomException(OwnedProductErrorCode.NOT_FOUND));

		Product product = (target.getProductId() == null) ? null
				: this.products.findById(target.getProductId()).orElse(null);
		if (product == null) {
			throw new CustomException(OwnedProductErrorCode.NOT_FOUND);
		}

		String category = product.getCategory();
		return CustomResponse.ok(new CareGuide(target.getId(), product.getName(), category,
				CARE_GUIDE.getOrDefault(category, CARE_GUIDE_DEFAULT)));
	}

	@Operation(summary = "AI 스타일링 (FR-023)",
			description = """
					인증 필수임. 보유 제품 하나에 어울리는 상품 3건과 근거 문구를 줌(화면 `CONTINUE-02`).

					함정 1: **상품 선정은 언제나 서버 규칙임.** 보유 제품과 카테고리가 겹치지 않는 것 중 스타일 태그 교집합 순으로 고르고, 결과끼리도 카테고리를 겹치지 않게 함. 코디 화면이라 의류를 먼저 채움.

					함정 2: **`reasonSource`가 `LLM`일 때만 AI라고 표기할 것.** 모델 호출이 실패하거나 느리거나 꺼져 있으면 규칙 문구로 조용히 폴백하고 그 값이 `RULE`이 됨. 폴백 화면에 AI 단정 문구를 그대로 두면 허위임.

					함정 3: 첫 항목만 `PERSONAL`이고 나머지는 고정 `GENERAL` 문구임(ADR-009). 모델 호출도 그래서 요청당 1회임.

					함정 4: 없는 id, 남의 제품, 삭제된 제품, **상품 연결이 끊긴 제품**을 모두 `OWNED404_2`로 응답함 — 관리 방법(#32)과 같은 계약임.""")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "추천 반환",
			content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "성공", value = """
					{
					  "isSuccess": true,
					  "code": "200",
					  "message": "OK",
					  "result": {
					    "product": { "productId": 4, "name": "미니 Aren 비세토스 카드 케이스", "category": "가죽 소품" },
					    "reasonSource": "LLM",
					    "results": [
					      {
					        "productId": 13,
					        "name": "로고 자카드 니트",
					        "category": "WOMAN TOP",
					        "price": 690000,
					        "officialUrl": "https://kr.mcmworldwide.com",
					        "reasonType": "PERSONAL",
					        "reason": "평소 미니멀 스타일을 즐기시네요"
					      }
					    ]
					  }
					}
					"""))),
			@ApiResponse(responseCode = "401", description = "비로그인",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1", value = """
									{ "isSuccess": false, "code": "AUTH401_1", "message": "로그인이 필요합니다", "result": null }
									"""))),
			@ApiResponse(responseCode = "404", description = "없는 id, 남의 제품, 삭제된 제품, 상품 연결이 끊긴 제품", content = @Content(
					mediaType = "application/json", examples = @ExampleObject(name = "OWNED404_2", value = """
							{ "isSuccess": false, "code": "OWNED404_2", "message": "보유 제품을 찾을 수 없습니다", "result": null }
							"""))) })
	@GetMapping("/{id}/styling")
	public CustomResponse<StylingService.Result> styling(@PathVariable Long id) {
		return CustomResponse.ok(this.styling.styling(this.currentMember.requireId(), id));
	}

	@Operation(summary = "보유 제품 삭제 (#21)",
			description = "인증 필수임. 본인 소유의 삭제되지 않은 제품만 소프트 삭제함(`deletedAt` 표시이며 행을 지우지 않음). **id를 경로가 아니라 요청 본문으로 받음** — DELETE에 본문이 필요하므로 클라이언트 설정에 주의할 것. `id` 누락, 없는 id, 남의 제품, 이미 삭제된 제품은 모두 `OWNED404_2`로 동일하게 응답함(존재 여부 노출 방지). 화면 미구현 상태의 엔드포인트임(명세서 5.0).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "삭제 성공",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "성공", value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": { "ok": true }
									}
									"""))),
			@ApiResponse(responseCode = "401", description = "비로그인",
					content = @Content(mediaType = "application/json",
							examples = @ExampleObject(name = "AUTH401_1", value = """
									{ "isSuccess": false, "code": "AUTH401_1", "message": "로그인이 필요합니다", "result": null }
									"""))),
			@ApiResponse(responseCode = "404", description = "id 누락 또는 없거나 남의 제품, 이미 삭제된 제품", content = @Content(
					mediaType = "application/json", examples = @ExampleObject(name = "OWNED404_2", value = """
							{ "isSuccess": false, "code": "OWNED404_2", "message": "제품 정보를 찾을 수 없습니다", "result": null }
							"""))) })
	@DeleteMapping
	@Transactional
	public CustomResponse<Map<String, Object>> delete(@RequestBody IdRequest request) {
		Long memberId = this.currentMember.requireId();

		if (request.id() == null) {
			throw new CustomException(OwnedProductErrorCode.NOT_FOUND);
		}
		OwnedProduct target = this.owned.findByIdAndMemberIdAndDeletedAtIsNull(request.id(), memberId)
			.orElseThrow(() -> new CustomException(OwnedProductErrorCode.NOT_FOUND));

		target.delete();
		return CustomResponse.ok(Map.of("ok", true));
	}

}
