package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.LoginRequest;
import com.secretsanta.gateway.dto.LoginResponse;
import com.secretsanta.gateway.service.AuthenticationGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationGatewayService authenticationGatewayService;

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return authenticationGatewayService.login(request);
    }
}
