package com.secretsanta.gateway.service;

import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.LoginRequest;
import com.secretsanta.gateway.dto.LoginResponse;
import com.secretsanta.gateway.security.PasswordEncryptor;
import com.secretsanta.gateway.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AuthenticationGatewayService {

    private static final String INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
    private static final String REQUEST_TIMEOUT = "REQUEST_TIMEOUT";

    private final CommandDispatcher dispatcher;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncryptor passwordEncryptor;

    @Value("${kafka.topics.user-commands}")
    private String userCommandsTopic;

    public Mono<ResponseEntity<LoginResponse>> login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            return Mono.just(ResponseEntity.<LoginResponse>badRequest().build());
        }

        AuthenticateUserCommand command = AuthenticateUserCommand.builder()
                .email(request.email())
                .encryptedPassword(passwordEncryptor.encrypt(request.password()))
                .build();
        command.initDefaults("AUTHENTICATE_USER");

        return dispatcher.send(
                        userCommandsTopic,
                        command,
                        "AUTHENTICATE_USER"
                )
                .map(this::toResponse)
                .onErrorResume(exception -> Mono.just(
                        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
                ));
    }

    private ResponseEntity<LoginResponse> toResponse(CommandResponse response) {
        if (response.isSuccess()
                && response.getData() instanceof UserAuthenticatedEvent event) {
            JwtTokenService.IssuedToken issuedToken =
                    jwtTokenService.issueFor(event.getUserId());
            return ResponseEntity.ok(new LoginResponse(
                    issuedToken.value(),
                    "Bearer",
                    issuedToken.expiresInSeconds(),
                    event.getUserId()
            ));
        }

        if (INVALID_CREDENTIALS.equals(response.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (REQUEST_TIMEOUT.equals(response.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
        }

        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }
}
