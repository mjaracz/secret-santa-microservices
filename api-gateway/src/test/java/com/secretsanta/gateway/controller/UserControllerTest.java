package com.secretsanta.gateway.controller;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateUserRequest;
import com.secretsanta.gateway.service.UserGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static com.secretsanta.gateway.dto.CommandResponse.REQUEST_TIMEOUT_ERROR_CODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest(UserController.class)
@Import(TestSecurityConfiguration.class)
class UserControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UserGatewayService userGatewayService;

    @Test
    void returnsCreatedForValidRegistration() {
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId("user-123")
                .email("user@example.com")
                .name("New User")
                .status(UserAccountStatus.PENDING_VERIFICATION)
                .build();
        event.initDefaults("USER_CREATED");

        when(userGatewayService.createUser(any(CreateUserRequest.class)))
                .thenReturn(
                        Mono.just(
                                CommandResponse.success(
                                        "command-123",
                                        event
                                )
                        )
                );

        webTestClient.post()
                .uri("/api/users")
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.status")
                .isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void returnsBadRequestForInvalidEmail() {
        CreateUserRequest request = new CreateUserRequest(
                "invalid-email",
                "New User",
                "correct-horse-battery-staple"
        );

        webTestClient.post()
                .uri("/api/users")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(userGatewayService);
    }

    @Test
    void returnsBadRequestForShortPassword() {
        CreateUserRequest request = new CreateUserRequest(
                "user@example.com",
                "New User",
                "short"
        );

        webTestClient.post()
                .uri("/api/users")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(userGatewayService);
    }

    @Test
    void returnsConflictForDuplicateEmail() {
        when(userGatewayService.createUser(any(CreateUserRequest.class)))
                .thenReturn(
                        Mono.just(
                                CommandResponse.failure(
                                        "command-123",
                                        "USER_EMAIL_ALREADY_EXISTS",
                                        "Email is already registered",
                                        "CREATE_USER"
                                )
                        )
                );

        webTestClient.post()
                .uri("/api/users")
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errorCode")
                .isEqualTo("USER_EMAIL_ALREADY_EXISTS");
    }

    @Test
    void returnsGatewayTimeout() {
        when(userGatewayService.createUser(any(CreateUserRequest.class)))
                .thenReturn(
                        Mono.just(
                                CommandResponse.failure(
                                        "command-123",
                                        REQUEST_TIMEOUT_ERROR_CODE,
                                        "Request timed out",
                                        "CREATE_USER"
                                )
                        )
                );

        webTestClient.post()
                .uri("/api/users")
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isEqualTo(504);
    }

    @Test
    void returnsInternalServerError() {
        when(userGatewayService.createUser(any(CreateUserRequest.class)))
                .thenReturn(
                        Mono.just(
                                CommandResponse.failure(
                                        "command-123",
                                        "INTERNAL_ERROR",
                                        "Internal error",
                                        "CREATE_USER"
                                )
                        )
                );

        webTestClient.post()
                .uri("/api/users")
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().is5xxServerError();
    }

    private CreateUserRequest validRequest() {
        return new CreateUserRequest(
                "user@example.com",
                "New User",
                "correct-horse-battery-staple"
        );
    }
}
