package com.mcmory.backend.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByPhoneAndDeletedAtIsNull(String phone);

	Optional<Member> findByIdAndDeletedAtIsNull(Long id);

}
