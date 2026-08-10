package com.mcmory.backend.notification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findByMemberIdOrderByIdDesc(Long memberId);

	List<Notification> findByMemberIdAndReadAtIsNull(Long memberId);

}
