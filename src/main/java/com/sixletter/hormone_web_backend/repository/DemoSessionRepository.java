package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.DemoSession;
import org.springframework.data.jpa.repository.JpaRepository;

/** PK 가 user_id 라 findById(userId) 가 곧 사용자별 조회다. */
public interface DemoSessionRepository extends JpaRepository<DemoSession, Long> {
}
