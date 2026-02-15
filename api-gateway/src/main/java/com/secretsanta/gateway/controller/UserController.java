package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateUserRequest;
import com.secretsanta.gateway.service.UserGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserGatewayService userGatewayService;

    @PostMapping
    public Mono<ResponseEntity<CommandResponse>> createUser(
            @RequestBody CreateUserRequest request) {
        return userGatewayService.createUser(request)
                .map(ResponseMapper::toResponseEntity);
    }
}
