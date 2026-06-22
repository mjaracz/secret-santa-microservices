package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CurrentUserResponse;
import com.secretsanta.gateway.dto.ResendVerificationRequest;
import com.secretsanta.gateway.dto.SignInRequest;
import com.secretsanta.gateway.service.AuthGatewayService;
import com.secretsanta.gateway.service.AuthenticationResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthGatewayService authGatewayService;
    private final String refreshCookieName;
    private final boolean refreshCookieSecure;
    private final String refreshCookieSameSite;
    private final Clock clock = Clock.systemUTC();

    public AuthController(
            AuthGatewayService authGatewayService,
            @Value("${security.refresh-cookie.name:refresh_token}") String refreshCookieName,
            @Value("${security.refresh-cookie.secure:true}") boolean refreshCookieSecure,
            @Value("${security.refresh-cookie.same-site:Strict}") String refreshCookieSameSite,
            Environment environment
    ) {
        this.authGatewayService = authGatewayService;
        this.refreshCookieName = refreshCookieName;
        this.refreshCookieSecure = refreshCookieSecure
                && !environment.acceptsProfiles(Profiles.of("local", "test"));
        this.refreshCookieSameSite = refreshCookieSameSite;
    }

    @PostMapping("/sign-in")
    public Mono<ResponseEntity<?>> signIn(
            @Valid @RequestBody SignInRequest request
    ) {
        return authGatewayService.signIn(request)
                .map(result -> authenticationResponse(result, false));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<?>> refresh(
            ServerWebExchange exchange
    ) {
        return authGatewayService.refresh(refreshToken(exchange))
                .map(result -> authenticationResponse(result, true));
    }

    @PostMapping("/sign-out")
    public Mono<ResponseEntity<?>> signOut(
            ServerWebExchange exchange
    ) {
        return authGatewayService.signOut(refreshToken(exchange))
                .map(response -> {
                    if (!response.isSuccess()) {
                        ResponseEntity<CommandResponse> failure =
                                ResponseMapper.toResponseEntity(response);
                        return ResponseEntity.status(failure.getStatusCode())
                                .header(
                                        org.springframework.http.HttpHeaders.SET_COOKIE,
                                        expiredRefreshCookie().toString()
                                )
                                .body(failure.getBody());
                    }
                    return ResponseEntity.noContent()
                            .header(
                                    org.springframework.http.HttpHeaders.SET_COOKIE,
                                    expiredRefreshCookie().toString()
                            )
                            .build();
                });
    }

    @GetMapping("/verify-email")
    public Mono<ResponseEntity<CommandResponse>> verifyEmail(
            @RequestParam String token
    ) {
        return authGatewayService.verifyEmail(token)
                .map(response -> response.isSuccess()
                        ? ResponseEntity.ok(response)
                        : ResponseMapper.toResponseEntity(response)
                );
    }

    @PostMapping("/verification/resend")
    public Mono<ResponseEntity<CommandResponse>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request
    ) {
        return authGatewayService.resendVerification(request.getEmail())
                .map(response -> ResponseMapper.toResponseEntity(
                        response,
                        HttpStatus.ACCEPTED
                ));
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return CurrentUserResponse.builder()
                .userId(jwt.getSubject())
                .email(jwt.getClaimAsString("email"))
                .name(jwt.getClaimAsString("name"))
                .roles(roles == null ? List.of() : roles)
                .build();
    }

    private ResponseEntity<?> authenticationResponse(
            AuthenticationResult result,
            boolean clearCookieOnFailure
    ) {
        if (!result.isSuccess()) {
            ResponseEntity<CommandResponse> failure =
                    ResponseMapper.toResponseEntity(result.commandResponse());
            if (!clearCookieOnFailure) {
                return failure;
            }
            return ResponseEntity.status(failure.getStatusCode())
                    .header(
                            org.springframework.http.HttpHeaders.SET_COOKIE,
                            expiredRefreshCookie().toString()
                    )
                    .body(failure.getBody());
        }

        return ResponseEntity.ok()
                .header(
                        org.springframework.http.HttpHeaders.CACHE_CONTROL,
                        "no-store"
                )
                .header(
                        org.springframework.http.HttpHeaders.PRAGMA,
                        "no-cache"
                )
                .header(
                        org.springframework.http.HttpHeaders.SET_COOKIE,
                        refreshCookie(result).toString()
                )
                .body(result.authenticationResponse());
    }

    private ResponseCookie refreshCookie(AuthenticationResult result) {
        long seconds = Math.max(
                0L,
                Duration.between(
                        clock.instant(),
                        java.time.Instant.ofEpochMilli(result.refreshTokenExpiresAt())
                ).toSeconds()
        );
        return ResponseCookie.from(refreshCookieName, result.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(seconds))
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

    private String refreshToken(ServerWebExchange exchange) {
        org.springframework.http.HttpCookie cookie = exchange
                .getRequest()
                .getCookies()
                .getFirst(refreshCookieName);
        return cookie == null ? null : cookie.getValue();
    }
}
