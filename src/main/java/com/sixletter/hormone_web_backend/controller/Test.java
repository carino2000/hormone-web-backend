package com.sixletter.hormone_web_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class Test {

    @GetMapping
    public ResponseEntity<?> testApi(){
        String str = "hello";
        return ResponseEntity.status(HttpStatus.OK).body(str);
    }
}
