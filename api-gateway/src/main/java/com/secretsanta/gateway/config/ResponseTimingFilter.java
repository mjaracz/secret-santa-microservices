package com.secretsanta.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Locale;

@Component
public class ResponseTimingFilter implements WebFilter, Ordered {

    private static final String SERVER_TIMING_HEADER = "Server-Timing";

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain
    ) {
        long startedAt = System.nanoTime();
        exchange.getResponse().beforeCommit(() -> {
            double durationMs = (System.nanoTime() - startedAt) / 1_000_000.0;
            exchange.getResponse().getHeaders().set(
                    SERVER_TIMING_HEADER,
                    "total;dur=" + String.format(Locale.ROOT, "%.3f", durationMs)
            );
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
