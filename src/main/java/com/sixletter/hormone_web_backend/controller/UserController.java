package com.sixletter.hormone_web_backend.controller;

import com.sixletter.hormone_web_backend.dto.UserDto;
import com.sixletter.hormone_web_backend.entity.User;
import com.sixletter.hormone_web_backend.exception.NotFoundException;
import com.sixletter.hormone_web_backend.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 CRUD. 엔티티를 직접 노출하지 않고 DTO 로만 주고받는다. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody UserDto request) {
        User saved = userRepository.save(request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.from(saved));
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> findAll() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(UserDto::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(UserDto.from(require(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> update(@PathVariable Long id, @RequestBody UserDto request) {
        User existing = require(id);
        request.applyTo(existing);
        return ResponseEntity.ok(UserDto.from(userRepository.save(existing)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userRepository.delete(require(id));
        return ResponseEntity.noContent().build();
    }

    private User require(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + id));
    }
}
