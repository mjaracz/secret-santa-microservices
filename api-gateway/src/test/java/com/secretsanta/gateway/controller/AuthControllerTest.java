package com.secretsanta.gateway.controller;

import com.secretsanta.common.user.UserRole;
import com.secretsanta.common.user.dto.AuthenticatedUserDto;
import com.secretsanta.gateway.dto.AuthenticationResponse;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.SignInRequest;
import com.secretsanta.gateway.dto.ResendVerificationRequest;
import com.secretsanta.gateway.service.AuthGatewayService;
import com.secretsanta.gateway.service.AuthenticationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(AuthController.class)
@Import(TestSecurityConfiguration.class)
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AuthGatewayService authGatewayService;

    @Test
    void signsInAndStoresRefreshTokenInHttpOnlyCookie() {
        when(authGatewayService.signIn(any(SignInRequest.class)))
                .thenReturn(Mono.just(successfulAuthentication()));

        webTestClient.post()
                .uri("/api/auth/sign-in")
                .bodyValue(new SignInRequest(
                        "user@example.com",
                        "correct-horse-battery-staple"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(
                        HttpHeaders.SET_COOKIE,
                        ".*refresh_token=raw-refresh-token.*HttpOnly.*"
                )
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("signed-access-token")
                .jsonPath("$.refreshToken").doesNotExist()
                .jsonPath("$.user.role").isEqualTo("USER");
    }

    @Test
    void mapsInvalidCredentialsToUnauthorized() {
        CommandResponse failure = CommandResponse.failure(
                "command-123",
                "AUTH_INVALID_CREDENTIALS",
                "Invalid email or password",
                "AUTHENTICATE_USER"
        );
        when(authGatewayService.signIn(any(SignInRequest.class)))
                .thenReturn(Mono.just(
                        new AuthenticationResult(failure, null, null, 0L)
                ));

        webTestClient.post()
                .uri("/api/auth/sign-in")
                .bodyValue(new SignInRequest(
                        "user@example.com",
                        "wrong-password"
                ))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode")
                .isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void refreshReadsCookieAndRotatesIt() {
        when(authGatewayService.refresh("old-refresh-token"))
                .thenReturn(Mono.just(successfulAuthentication()));

        webTestClient.post()
                .uri("/api/auth/refresh")
                .cookie("refresh_token", "old-refresh-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(
                        HttpHeaders.SET_COOKIE,
                        ".*refresh_token=raw-refresh-token.*"
                );
    }

    @Test
    void rejectsMissingRefreshCookie() {
        CommandResponse failure = CommandResponse.failure(
                "local-authentication",
                "AUTH_REFRESH_TOKEN_INVALID",
                "Refresh token is missing",
                "REFRESH_SESSION"
        );
        when(authGatewayService.refresh(null))
                .thenReturn(Mono.just(
                        new AuthenticationResult(failure, null, null, 0L)
                ));

        webTestClient.post()
                .uri("/api/auth/refresh")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void verifiesEmailUsingRawTokenFromLink() {
        when(authGatewayService.verifyEmail("raw-verification-token"))
                .thenReturn(Mono.just(
                        CommandResponse.success("command-123", null)
                ));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/auth/verify-email")
                        .queryParam("token", "raw-verification-token")
                        .build())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void acceptsVerificationResendWithoutAccountEnumeration() {
        when(authGatewayService.resendVerification("user@example.com"))
                .thenReturn(Mono.just(
                        CommandResponse.success("command-123", null)
                ));

        webTestClient.post()
                .uri("/api/auth/verification/resend")
                .bodyValue(new ResendVerificationRequest("user@example.com"))
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void signsOutAndExpiresRefreshCookie() {
        when(authGatewayService.signOut("refresh-token"))
                .thenReturn(Mono.just(
                        CommandResponse.success("command-123", null)
                ));

        webTestClient.post()
                .uri("/api/auth/sign-out")
                .cookie("refresh_token", "refresh-token")
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valueMatches(
                        HttpHeaders.SET_COOKIE,
                        ".*refresh_token=;.*Max-Age=0.*"
                );
    }

    private AuthenticationResult successfulAuthentication() {
        AuthenticatedUserDto user = AuthenticatedUserDto.builder()
                .userId("user-123")
                .email("user@example.com")
                .name("User")
                .role(UserRole.USER)
                .build();
        AuthenticationResponse response = AuthenticationResponse.builder()
                .accessToken("signed-access-token")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(user)
                .build();
        return new AuthenticationResult(
                CommandResponse.success("command-123", null),
                response,
                "raw-refresh-token",
                Instant.now().plusSeconds(3600).toEpochMilli()
        );
    }
}
