package com.mcmory.backend.friend;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendRepository extends JpaRepository<Friend, Long> {

	List<Friend> findByOwnerMemberIdAndDeletedAtIsNullOrderById(Long ownerMemberId);

	Optional<Friend> findByIdAndOwnerMemberIdAndDeletedAtIsNull(Long id, Long ownerMemberId);

	Optional<Friend> findFirstByOwnerMemberIdAndNameAndDeletedAtIsNull(Long ownerMemberId, String name);

}
