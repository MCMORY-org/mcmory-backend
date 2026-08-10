package com.mcmory.backend.owned;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;
import com.mcmory.backend.global.apiPayload.code.OwnedProductErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.product.Product;
import com.mcmory.backend.product.ProductRepository;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owned")
public class OwnedProductController {

	private final OwnedProductRepository owned;

	private final ProductRepository products;

	private final CurrentMember currentMember;

	public OwnedProductController(OwnedProductRepository owned, ProductRepository products,
			CurrentMember currentMember) {
		this.owned = owned;
		this.products = products;
		this.currentMember = currentMember;
	}

	public record RegisterRequest(String serial) {
	}

	public record IdRequest(Long id) {
	}

	public record ProductView(String name, String emoji) {
	}

	public record OwnedView(Long id, String source, String serialMemo, LocalDate createdAt, ProductView product) {
	}

	@GetMapping
	@Transactional(readOnly = true)
	public CustomResponse<Map<String, Object>> list() {
		Long memberId = this.currentMember.requireId();

		List<OwnedView> list = this.owned.findByMemberIdAndDeletedAtIsNullOrderById(memberId).stream().map((row) -> {
			Product product = (row.getProductId() == null) ? null
					: this.products.findById(row.getProductId()).orElse(null);

			return new OwnedView(row.getId(), row.getSource(), row.getSerialMemo(),
					(row.getCreatedAt() == null) ? null : row.getCreatedAt().toLocalDate(),
					(product == null) ? null : new ProductView(product.getName(), product.emoji()));
		}).toList();

		return CustomResponse.ok(Map.of("list", list));
	}

	/**
	 * FR-028 데모 시리얼 매칭 등록임. 대소문자 무시 완전 일치만 하고 불일치는 안내만 하며 등록하지 않음.
	 *
	 * 실패 문구에 인증이나 정품 표현을 쓰지 않음(ADR-004, NFR-004) — 조회 출처가 없어 정품 여부를 말할 근거가 없음.
	 */
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
		return CustomResponse.ok(Map.of("ok", true, "product", new ProductView(product.getName(), product.emoji())));
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

	public record CareGuide(Long ownedProductId, String productName, String category, List<String> items) {
	}

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
