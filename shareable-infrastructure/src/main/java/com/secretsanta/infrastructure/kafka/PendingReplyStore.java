package com.secretsanta.infrastructure.kafka;

import com.secretsanta.common.BaseEvent;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PendingReplyStore {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private final ConcurrentHashMap<String, CompletableFuture<BaseEvent>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<BaseEvent> register(String correlationId) {
        return register(correlationId, DEFAULT_TIMEOUT);
    }

    public CompletableFuture<BaseEvent> register(String correlationId, Duration timeout) {
        CompletableFuture<BaseEvent> future = new CompletableFuture<BaseEvent>()
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        future.whenComplete((result, ex) -> pending.remove(correlationId));
        pending.put(correlationId, future);
        log.debug("Registered pending reply for correlationId={}", correlationId);
        return future;
    }

    public boolean complete(String correlationId, BaseEvent event) {
        CompletableFuture<BaseEvent> future = pending.remove(correlationId);
        if (future != null) {
            log.debug("Completing pending reply for correlationId={}", correlationId);
            return future.complete(event);
        }
        return false;
    }

    int pendingCount() {
        return pending.size();
    }
}
