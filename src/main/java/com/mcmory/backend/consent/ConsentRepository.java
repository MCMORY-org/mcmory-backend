package com.mcmory.backend.consent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

	List<Consent> findByMemberIdOrderById(Long memberId);

}
