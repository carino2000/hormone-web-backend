package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사용자. 모델의 정적 피처(birth_date -> birth_year, age_of_first_menarche, ethnicity)를 포함한다.
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
    private Long id;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "height", precision = 4, scale = 1)
    private BigDecimal height;

    @Column(name = "weight", precision = 4, scale = 1)
    private BigDecimal weight;

    @Column(name = "age_of_first_menarche")
    private Integer ageOfFirstMenarche;

    @Column(name = "ethnicity", length = 32)
    private String ethnicity;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
