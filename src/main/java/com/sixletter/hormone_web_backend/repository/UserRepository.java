package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByExternalUid(String externalUid);

    Optional<User> findBySourceParticipantId(Integer sourceParticipantId);
}
