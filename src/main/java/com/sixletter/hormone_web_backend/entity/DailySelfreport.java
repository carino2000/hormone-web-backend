package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * daily_selfreports : 사용자 자가입력 증상. 척도 0(Not at all) ~ 5(Very high).
 * reported_at = 증상 발생 시점, entered_at = 앱에 입력한 시각(다를 수 있음).
 */
@Entity
@Table(
        name = "daily_selfreports",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_reported", columnNames = {"user_id", "reported_at"}),
        indexes = @Index(name = "idx_user_day", columnList = "user_id, reported_on")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySelfreport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sr_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Column(name = "reported_on", insertable = false, updatable = false,
            columnDefinition = "DATE GENERATED ALWAYS AS (DATE(reported_at)) STORED")
    private LocalDate reportedOn;

    @Column(name = "entered_at")
    private LocalDateTime enteredAt;

    @Column(name = "flow_volume", columnDefinition = "TINYINT UNSIGNED")
    private Integer flowVolume;

    /** 예시: not at all, dark brown, bright red 등. 고정 목록이 아니라 VARCHAR. */
    @Column(name = "flow_color", length = 32)
    private String flowColor;

    @Column(name = "appetite", columnDefinition = "TINYINT UNSIGNED")
    private Integer appetite;

    @Column(name = "exerciselevel", columnDefinition = "TINYINT UNSIGNED")
    private Integer exerciseLevel;

    @Column(name = "headaches", columnDefinition = "TINYINT UNSIGNED")
    private Integer headaches;

    @Column(name = "cramps", columnDefinition = "TINYINT UNSIGNED")
    private Integer cramps;

    @Column(name = "sorebreasts", columnDefinition = "TINYINT UNSIGNED")
    private Integer soreBreasts;

    @Column(name = "fatigue", columnDefinition = "TINYINT UNSIGNED")
    private Integer fatigue;

    @Column(name = "sleepissue", columnDefinition = "TINYINT UNSIGNED")
    private Integer sleepIssue;

    @Column(name = "moodswing", columnDefinition = "TINYINT UNSIGNED")
    private Integer moodSwing;

    @Column(name = "stress", columnDefinition = "TINYINT UNSIGNED")
    private Integer stress;

    @Column(name = "foodcravings", columnDefinition = "TINYINT UNSIGNED")
    private Integer foodCravings;

    @Column(name = "indigestion", columnDefinition = "TINYINT UNSIGNED")
    private Integer indigestion;

    @Column(name = "bloating", columnDefinition = "TINYINT UNSIGNED")
    private Integer bloating;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
