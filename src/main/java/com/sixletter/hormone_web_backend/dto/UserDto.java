package com.sixletter.hormone_web_backend.dto;

import com.sixletter.hormone_web_backend.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 사용자 입출력. createdAt/updatedAt 은 서버 관리 값이라 응답에만 담는다. */
public record UserDto(
        Long id,
        String name,
        LocalDate birthDate,
        BigDecimal height,
        BigDecimal weight,
        Integer ageOfFirstMenarche,
        String ethnicity
) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getName(), u.getBirthDate(),
                u.getHeight(), u.getWeight(), u.getAgeOfFirstMenarche(), u.getEthnicity());
    }

    public User toEntity() {
        return User.builder()
                .name(name).birthDate(birthDate).height(height).weight(weight)
                .ageOfFirstMenarche(ageOfFirstMenarche).ethnicity(ethnicity)
                .build();
    }

    /** 기존 엔티티에 덮어쓰기 (PUT). id 는 건드리지 않는다. */
    public void applyTo(User target) {
        target.setName(name);
        target.setBirthDate(birthDate);
        target.setHeight(height);
        target.setWeight(weight);
        target.setAgeOfFirstMenarche(ageOfFirstMenarche);
        target.setEthnicity(ethnicity);
    }
}
