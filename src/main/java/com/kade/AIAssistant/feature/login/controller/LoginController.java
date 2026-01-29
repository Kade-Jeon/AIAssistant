package com.kade.AIAssistant.feature.login.controller;

import com.kade.AIAssistant.domain.reqeust.LoginRequest;
import com.kade.AIAssistant.domain.response.LoginResponse;
import com.kade.AIAssistant.feature.login.service.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = loginService.login(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
