package com.secretsanta.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import com.secretsanta.gateway.security.JwtService;
import com.secretsanta.gateway.security.AccessToken;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.common.user.dto.AuthenticatedUserDto;

@SpringBootTest(properties = {
		"server.port=0",
		"spring.kafka.bootstrap-servers=localhost:9092",
		"spring.kafka.consumer.group-id=api-gateway-test",
		"kafka.topics.user-commands=user.commands",
		"kafka.topics.group-commands=group.commands",
		"kafka.topics.user-events=user.events",
		"kafka.topics.group-events=group.events"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiGatewayApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtService jwtService;

    @Test
    void contextLoads() {
    }

    @Test
    void rejectsProtectedEndpointWithoutBearerToken() {
        webTestClient.get()
                .uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode")
                .isEqualTo("AUTH_UNAUTHORIZED");
    }

    @Test
    void acceptsValidRs256BearerToken() {
        AccessToken token = jwtService.issue(
                AuthenticatedUserDto.builder()
                        .userId("user-123")
                        .email("user@example.com")
                        .name("User")
                        .role(UserRole.USER)
                        .build()
        );

        webTestClient.get()
                .uri("/api/auth/me")
                .headers(headers -> headers.setBearerAuth(token.value()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("user-123")
                .jsonPath("$.roles[0]").isEqualTo("USER");
    }
}
