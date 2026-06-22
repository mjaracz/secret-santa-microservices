package com.secretsanta.gateway.service;

import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.commands.RefreshSessionCommand;
import com.secretsanta.common.user.commands.ResendEmailVerificationCommand;
import com.secretsanta.common.user.commands.RevokeSessionCommand;
import com.secretsanta.common.user.commands.VerifyEmailCommand;
import com.secretsanta.common.user.dto.AuthenticatedUserDto;
import com.secretsanta.common.user.events.SessionRefreshedEvent;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
import com.secretsanta.gateway.dto.AuthenticationResponse;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.SignInRequest;
import com.secretsanta.gateway.security.AccessToken;
import com.secretsanta.gateway.security.GatewayTokenService;
import com.secretsanta.gateway.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthGatewayService {

    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";

    private final CommandDispatcher dispatcher;
    private final GatewayTokenService tokenService;
    private final JwtService jwtService;

    @Value("${kafka.topics.auth-commands}")
    private String authCommandsTopic;

    public Mono<AuthenticationResult> signIn(SignInRequest request) {
        String refreshToken = tokenService.generate();
        AuthenticateUserCommand command = AuthenticateUserCommand.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .refreshTokenHash(tokenService.hash(refreshToken))
                .build();
        command.initDefaults("AUTHENTICATE_USER");

        return dispatcher.send(authCommandsTopic, command, "AUTHENTICATE_USER")
                .map(response -> authenticationResult(
                        response,
                        refreshToken,
                        UserAuthenticatedEvent.class
                ));
    }

    public Mono<AuthenticationResult> refresh(String currentRefreshToken) {
        if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
            return Mono.just(failedAuthentication(
                    "AUTH_REFRESH_TOKEN_INVALID",
                    "Refresh token is missing",
                    "REFRESH_SESSION"
            ));
        }

        String replacementToken = tokenService.generate();
        RefreshSessionCommand command = RefreshSessionCommand.builder()
                .currentTokenHash(tokenService.hash(currentRefreshToken))
                .replacementTokenHash(tokenService.hash(replacementToken))
                .build();
        command.initDefaults("REFRESH_SESSION");

        return dispatcher.send(authCommandsTopic, command, "REFRESH_SESSION")
                .map(response -> authenticationResult(
                        response,
                        replacementToken,
                        SessionRefreshedEvent.class
                ));
    }

    public Mono<CommandResponse> signOut(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.just(CommandResponse.success("local-sign-out", null));
        }

        RevokeSessionCommand command = RevokeSessionCommand.builder()
                .tokenHash(tokenService.hash(refreshToken))
                .build();
        command.initDefaults("REVOKE_SESSION");
        return dispatcher.send(authCommandsTopic, command, "REVOKE_SESSION");
    }

    public Mono<CommandResponse> verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Mono.just(CommandResponse.failure(
                    "local-verification",
                    "USER_VERIFICATION_TOKEN_INVALID",
                    "Verification token is required",
                    "VERIFY_EMAIL"
            ));
        }

        VerifyEmailCommand command = VerifyEmailCommand.builder()
                .tokenHash(tokenService.hash(rawToken))
                .build();
        command.initDefaults("VERIFY_EMAIL");
        return dispatcher.send(authCommandsTopic, command, "VERIFY_EMAIL");
    }

    public Mono<CommandResponse> resendVerification(String email) {
        ResendEmailVerificationCommand command = ResendEmailVerificationCommand.builder()
                .email(email)
                .build();
        command.initDefaults("RESEND_EMAIL_VERIFICATION");
        return dispatcher.send(
                authCommandsTopic,
                command,
                "RESEND_EMAIL_VERIFICATION"
        );
    }

    private AuthenticationResult authenticationResult(
            CommandResponse commandResponse,
            String refreshToken,
            Class<?> expectedEventType
    ) {
        if (!commandResponse.isSuccess()) {
            return new AuthenticationResult(commandResponse, null, null, 0L);
        }

        Object event = commandResponse.getData();
        AuthenticatedUserDto user;
        long refreshTokenExpiresAt;

        if (expectedEventType == UserAuthenticatedEvent.class
                && event instanceof UserAuthenticatedEvent authenticated) {
            user = authenticated.getUser();
            refreshTokenExpiresAt = authenticated.getRefreshTokenExpiresAt();
        } else if (expectedEventType == SessionRefreshedEvent.class
                && event instanceof SessionRefreshedEvent refreshed) {
            user = refreshed.getUser();
            refreshTokenExpiresAt = refreshed.getRefreshTokenExpiresAt();
        } else {
            return failedAuthentication(
                    INTERNAL_ERROR_CODE,
                    "Unexpected authentication response",
                    commandResponse.getOriginalCommandType()
            );
        }

        AccessToken accessToken = jwtService.issue(user);
        AuthenticationResponse authenticationResponse = AuthenticationResponse.builder()
                .accessToken(accessToken.value())
                .tokenType("Bearer")
                .expiresIn(accessToken.expiresInSeconds())
                .user(user)
                .build();
        return new AuthenticationResult(
                commandResponse,
                authenticationResponse,
                refreshToken,
                refreshTokenExpiresAt
        );
    }

    private AuthenticationResult failedAuthentication(
            String errorCode,
            String message,
            String commandType
    ) {
        return new AuthenticationResult(
                CommandResponse.failure(
                        "local-authentication",
                        errorCode,
                        message,
                        commandType
                ),
                null,
                null,
                0L
        );
    }
}
