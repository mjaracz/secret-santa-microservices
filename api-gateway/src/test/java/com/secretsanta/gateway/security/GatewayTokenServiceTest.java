package com.secretsanta.gateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayTokenServiceTest {

    @Test
    void generatesHighEntropyTokenAndStableSha256Hash() {
        GatewayTokenService service = new GatewayTokenService();

        String token = service.generate();
        String hash = service.hash(token);

        assertThat(token).hasSize(43);
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).isEqualTo(service.hash(token));
        assertThat(hash).isNotEqualTo(token);
    }
}
