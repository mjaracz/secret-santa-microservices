package com.secretsanta.gateway.security;

import com.secretsanta.common.user.UserRole;
import com.secretsanta.common.user.dto.AuthenticatedUserDto;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void signsAndValidatesRs256AccessTokenWithExpectedClaims() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        JwtKeyMaterial keys = new JwtKeyMaterial(
                (RSAPublicKey) keyPair.getPublic(),
                (RSAPrivateKey) keyPair.getPrivate()
        );
        SecurityConfig config = new SecurityConfig();
        JwtEncoder encoder = config.jwtEncoder(keys);
        ReactiveJwtDecoder decoder = config.jwtDecoder(
                keys,
                "test-issuer",
                "test-audience"
        );
        JwtService service = new JwtService(
                encoder,
                "test-issuer",
                "test-audience",
                Duration.ofMinutes(15)
        );

        AccessToken token = service.issue(
                AuthenticatedUserDto.builder()
                        .userId("user-123")
                        .email("user@example.com")
                        .name("User")
                        .role(UserRole.USER)
                        .build()
        );
        Jwt jwt = decoder.decode(token.value()).block();

        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo("user-123");
        assertThat(jwt.getAudience()).containsExactly("test-audience");
        assertThat(jwt.getClaimAsStringList("roles"))
                .containsExactly("USER");
        assertThat(token.expiresInSeconds()).isEqualTo(900L);
    }
}
