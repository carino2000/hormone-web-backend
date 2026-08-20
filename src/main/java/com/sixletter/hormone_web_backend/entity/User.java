package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * users : 사용자 기본 정보 + 정적 피처 (subject-info.csv)
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "external_uid", nullable = false, unique = true, length = 64)
    private String externalUid;

    @Column(name = "source_participant_id", columnDefinition = "TINYINT UNSIGNED")
    private Integer sourceParticipantId;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDateTime enrolledAt;

    @Builder.Default
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Seoul";

    @Column(name = "birth_year", columnDefinition = "SMALLINT UNSIGNED")
    private Integer birthYear;

    @Column(name = "age_of_first_menarche", columnDefinition = "TINYINT UNSIGNED")
    private Integer ageOfFirstMenarche;

    /**
     * ENUM('East Asian','Southeast Asian','White','Middle Eastern',
     *      'Latina','Caribbean','South Asian','African')
     * 값 목록이 아직 안정적이지 않을 수 있어 Java enum 대신 String 으로 매핑.
     */
    @Column(name = "ethnicity", length = 32)
    private String ethnicity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
