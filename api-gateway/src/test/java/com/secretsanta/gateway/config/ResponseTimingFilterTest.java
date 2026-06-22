package com.secretsanta.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

class ResponseTimingFilterTest {

    @Test
    void reportsEndToEndRequestDurationInMilliseconds() {
        WebTestClient client = WebTestClient
                .bindToWebHandler(exchange -> Mono
                        .delay(Duration.ofMillis(5))
                        .then(exchange.getResponse().setComplete()))
                .webFilter(new ResponseTimingFilter())
                .build();

        client.get()
                .uri("/timed")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(
                        "Server-Timing",
                        "total;dur=\\d+\\.\\d{3}"
                );
    }
}
