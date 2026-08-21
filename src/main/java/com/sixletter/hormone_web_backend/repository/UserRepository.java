package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
