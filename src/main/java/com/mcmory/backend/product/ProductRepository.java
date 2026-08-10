package com.mcmory.backend.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

	List<Product> findByPriceBetweenOrderById(int minPrice, int maxPrice);

	List<Product> findAllByOrderById();

	/** FR-028: 대소문자 무시 완전 일치만임. 부분 일치를 허용하면 없는 번호가 통과함. */
	@Query("SELECT p FROM Product p WHERE UPPER(p.demoSerial) = :serial")
	Optional<Product> findByDemoSerialIgnoringCase(@Param("serial") String serial);

}
