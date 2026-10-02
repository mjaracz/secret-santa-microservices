package com.secretsanta.gateway.controller;

import com.secretsanta.common.group.events.MyGroupsFetchedEvent;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.security.JwtTokenService;
import com.secretsanta.gateway.security.SecurityConfig;
import com.secretsanta.gateway.service.GroupGatewayService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = GroupController.class)
@Import(SecurityConfig.class)
class GroupControllerSecurityTest {

    private static final String ISSUER = "https://secret-santa-test";
    private static final String AUDIENCE = "secret-santa-api-test";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final byte[] SIGNING_KEY_BYTES =
            "01234567890123456789012345678901".getBytes();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private GroupGatewayService groupGatewayService;

    @DynamicPropertySource
    static void registerJwtProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "security.jwt.secret-base64",
                () -> Base64.getEncoder().encodeToString(SIGNING_KEY_BYTES)
        );
        registry.add("security.jwt.issuer", () -> ISSUER);
        registry.add("security.jwt.audience", () -> AUDIENCE);
    }

    @Test
    void requiresBearerTokenForMyGroups() {
        webTestClient.get()
                .uri("/api/groups/me")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(groupGatewayService);
    }

    @Test
    void passesJwtSubjectToGroupServiceForMyGroups() {
        MyGroupsFetchedEvent event = MyGroupsFetchedEvent.builder()
                .groups(java.util.List.of())
                .build();
        event.initDefaults("MY_GROUPS_FETCHED");
        when(groupGatewayService.getMyGroups(USER_ID))
                .thenReturn(reactor.core.publisher.Mono.just(
                        CommandResponse.success("command-1", event)
                ));
        String token = tokenFrom(
                new SecretKeySpec(SIGNING_KEY_BYTES, "HmacSHA256"),
                ISSUER,
                AUDIENCE
        );

        webTestClient.get()
                .uri("/api/groups/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        verify(groupGatewayService).getMyGroups(USER_ID);
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        String token = tokenFrom(
                new SecretKeySpec(SIGNING_KEY_BYTES, "HmacSHA256"),
                ISSUER,
                "another-api"
        );

        webTestClient.get()
                .uri("/api/groups/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(groupGatewayService);
    }

    @Test
    void rejectsTokenSignedByDifferentKey() {
        byte[] differentKey = new byte[32];
        java.util.Arrays.fill(differentKey, (byte) 1);
        String token = tokenFrom(
                new SecretKeySpec(differentKey, "HmacSHA256"),
                ISSUER,
                AUDIENCE
        );

        webTestClient.get()
                .uri("/api/groups/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(groupGatewayService);
    }

    private String tokenFrom(SecretKey key, String issuer, String audience) {
        JwtTokenService tokenService = new JwtTokenService(
                new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key)),
                issuer,
                audience
        );
        return tokenService.issueFor(USER_ID).value();
    }
}
