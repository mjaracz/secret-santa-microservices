package com.secretsanta.infrastructure.kafka;

import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.group.events.GroupCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingReplyStoreTest {

    private PendingReplyStore store;

    @BeforeEach
    void setUp() {
        store = new PendingReplyStore();
    }

    @Test
    void register_and_complete_returns_event() throws Exception {
        CompletableFuture<BaseEvent> future = store.register("corr-1");

        GroupCreatedEvent event = GroupCreatedEvent.builder()
                .groupId("group-123")
                .name("Test Group")
                .build();
        event.initDefaults("GROUP_CREATED");

        boolean completed = store.complete("corr-1", event);

        assertThat(completed).isTrue();
        assertThat(future.get()).isEqualTo(event);
        assertThat(store.pendingCount()).isZero();
    }

    @Test
    void complete_with_unknown_correlationId_returns_false() {
        store.register("corr-1");

        boolean completed = store.complete("unknown-id", GroupCreatedEvent.builder().build());

        assertThat(completed).isFalse();
        assertThat(store.pendingCount()).isEqualTo(1);
    }

    @Test
    void timeout_throws_timeout_exception() {
        CompletableFuture<BaseEvent> future = store.register("corr-1", Duration.ofMillis(50));

        assertThatThrownBy(() -> future.get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void timeout_cleans_up_pending_entry() throws Exception {
        CompletableFuture<BaseEvent> future = store.register("corr-1", Duration.ofMillis(50));

        try {
            future.get();
        } catch (ExecutionException e) {
            // expected timeout
        }

        // Give whenComplete callback a moment to execute
        Thread.sleep(20);
        assertThat(store.pendingCount()).isZero();
    }

    @Test
    void double_complete_returns_false() {
        store.register("corr-1");

        GroupCreatedEvent event = GroupCreatedEvent.builder().groupId("g1").build();

        boolean first = store.complete("corr-1", event);
        boolean second = store.complete("corr-1", event);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }
}
