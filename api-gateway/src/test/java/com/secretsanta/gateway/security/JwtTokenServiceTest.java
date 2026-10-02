package com.secretsanta.gateway.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String ISSUER = "https://secret-santa-test";
    private static final String AUDIENCE = "secret-santa-api-test";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    private JwtTokenService jwtTokenService;
    private NimbusReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(new byte[32], "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<SecurityContext>(key)
        );
        jwtTokenService = new JwtTokenService(encoder, ISSUER, AUDIENCE);
        jwtDecoder = NimbusReactiveJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
    }

    @Test
    void issuesSignedShortLivedTokenWithUserSubjectAndAudience() {
        JwtTokenService.IssuedToken issued = jwtTokenService.issueFor(USER_ID);

        Jwt decoded = jwtDecoder.decode(issued.value()).block();

        assertThat(decoded).isNotNull();
        assertThat(decoded.getSubject()).isEqualTo(USER_ID);
        assertThat(decoded.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
    }
}
