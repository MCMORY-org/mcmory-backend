package com.mcmory.backend.taste;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TasteProfileRepository extends JpaRepository<TasteProfile, Long> {

	Optional<TasteProfile> findByFriendId(Long friendId);

	List<TasteProfile> findByFriendIdIn(List<Long> friendIds);

	void deleteByFriendId(Long friendId);

}
