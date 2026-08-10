package com.mcmory.backend.owned;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnedProductRepository extends JpaRepository<OwnedProduct, Long> {

	List<OwnedProduct> findByMemberIdAndDeletedAtIsNullOrderById(Long memberId);

	Optional<OwnedProduct> findByIdAndMemberIdAndDeletedAtIsNull(Long id, Long memberId);

	Optional<OwnedProduct> findByMemberIdAndProductIdAndSourceAndDeletedAtIsNull(Long memberId, Long productId,
			String source);

	/** FR-020 재클릭 멱등 판정임. gift_id에 유니크를 걸면 soft delete된 행이 자리를 영구 점유해 재등록이 막힘. */
	Optional<OwnedProduct> findByGiftIdAndDeletedAtIsNull(Long giftId);

}
