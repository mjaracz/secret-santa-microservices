package com.secretsanta.infrastructure.kafka;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KafkaServiceBus {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;
    private final PendingReplyStore replyStore;
    private final Map<Class<? extends BaseCommand>, CommandHandlerEntry<?>> commandHandlers = new HashMap<>();
    private final Map<Class<? extends BaseEvent>, EventHandlerEntry<?>> eventHandlers = new HashMap<>();

    public KafkaServiceBus(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this(kafkaTemplate, objectMapper, buildDefaultRetryTemplate(), null);
    }

    public KafkaServiceBus(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
            RetryTemplate retryTemplate) {
        this(kafkaTemplate, objectMapper, retryTemplate, null);
    }

    public KafkaServiceBus(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
            PendingReplyStore replyStore) {
        this(kafkaTemplate, objectMapper, buildDefaultRetryTemplate(), replyStore);
    }

    public KafkaServiceBus(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
            RetryTemplate retryTemplate, PendingReplyStore replyStore) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;
        this.replyStore = replyStore;
    }

    @FunctionalInterface
    public interface CommandHandler<T extends BaseCommand> {
        void handle(T command);
    }

    @FunctionalInterface
    public interface EventHandler<T extends BaseEvent> {
        void handle(T event);
    }

    @FunctionalInterface
    public interface FailureEmitter {
        void emit(BaseCommand command, String reason);
    }

    private record CommandHandlerEntry<T extends BaseCommand>(Class<T> type, CommandHandler<T> handler) {
        void dispatch(BaseCommand command) {
            handler.handle(type.cast(command));
        }
    }

    private record EventHandlerEntry<T extends BaseEvent>(Class<T> type, EventHandler<T> handler) {
        void dispatch(BaseEvent event) {
            handler.handle(type.cast(event));
        }
    }

    public <T extends BaseCommand> void registerCommandHandler(Class<T> commandType, CommandHandler<T> handler) {
        commandHandlers.put(commandType, new CommandHandlerEntry<>(commandType, handler));
        log.info("Registered command handler for: {}", commandType.getSimpleName());
    }

    public <T extends BaseEvent> void registerEventHandler(Class<T> eventType, EventHandler<T> handler) {
        eventHandlers.put(eventType, new EventHandlerEntry<>(eventType, handler));
        log.info("Registered event handler for: {}", eventType.getSimpleName());
    }

    public void handleCommandMessage(String json) {
        handleCommandMessage(json, null);
    }

    public void handleCommandMessage(String json, FailureEmitter failureEmitter) {
        final BaseCommand command;
        try {
            command = objectMapper.readValue(json, BaseCommand.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot deserialize Kafka command", exception);
        }

        log.info("Received command: type={}, id={}", command.getCommandType(), command.getCommandId());
        try {
            retryTemplate.execute(context -> {
                dispatchCommand(command);
                return null;
            });
        } catch (IllegalArgumentException e) {
            log.error("Business rule violation: {}", e.getMessage());
            if (failureEmitter != null) {
                failureEmitter.emit(command, e.getMessage());
                return;
            }
            throw e;
        } catch (RuntimeException e) {
            log.error("Error processing command after retries exhausted", e);
            throw e;
        }
    }

    public void handleEventMessage(String json) {
        final BaseEvent event;
        try {
            event = objectMapper.readValue(json, BaseEvent.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot deserialize Kafka event", exception);
        }

        log.info("Received event: type={}, id={}", event.getEventType(), event.getEventId());
        try {
            retryTemplate.execute(context -> {
                dispatchEvent(event);
                return null;
            });
            completeReply(event);
        } catch (RuntimeException e) {
            log.error("Error processing event after retries exhausted", e);
            throw e;
        }
    }

    public void emitCommand(String topic, String key, BaseCommand command) {
        log.info("Emitting command: type={}, key={}, topic={}", command.getCommandType(), key, topic);
        publish(topic, key, command);
    }

    public void emitEvent(String topic, String key, BaseEvent event) {
        log.info("Emitting event: type={}, key={}, topic={}", event.getEventType(), key, topic);
        publish(topic, key, event);
    }

    public CompletableFuture<BaseEvent> sendAndReceive(String topic, String key, BaseCommand command) {
        return sendAndReceive(topic, key, command, Duration.ofSeconds(5));
    }

    public CompletableFuture<BaseEvent> sendAndReceive(String topic, String key, BaseCommand command,
            Duration timeout) {
        if (replyStore == null) {
            throw new IllegalStateException("PendingReplyStore not configured — sendAndReceive not available");
        }
        CompletableFuture<BaseEvent> future = replyStore.register(command.getCommandId(), timeout);
        try {
            emitCommand(topic, key, command);
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    public void completeReply(BaseEvent event) {
        if (replyStore != null && event.getCorrelationId() != null) {
            replyStore.complete(event.getCorrelationId(), event);
        }
    }

    private void dispatchCommand(BaseCommand command) {
        CommandHandlerEntry<?> entry = commandHandlers.get(command.getClass());
        if (entry != null) {
            entry.dispatch(command);
        } else {
            throw new IllegalStateException(
                    "No handler registered for command type: " + command.getClass().getSimpleName());
        }
    }

    private void dispatchEvent(BaseEvent event) {
        EventHandlerEntry<?> entry = eventHandlers.get(event.getClass());
        if (entry != null) {
            entry.dispatch(event);
        } else {
            log.warn("No handler registered for event type: {}", event.getClass().getSimpleName());
        }
    }

    private void publish(String topic, String key, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(topic, key, json).get(10, TimeUnit.SECONDS);
            log.info("Published message: key={}, topic={}", key, topic);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while publishing message: key=" + key + ", topic=" + topic,
                    e);
        } catch (Exception e) {
            log.error("Error publishing message: key={}, topic={}", key, topic, e);
            throw new IllegalStateException(
                    "Kafka did not acknowledge message: key=" + key + ", topic=" + topic,
                    e);
        }
    }

    private static RetryTemplate buildDefaultRetryTemplate() {
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new SimpleRetryPolicy(1,
                Map.of(Exception.class, true, IllegalArgumentException.class, false), true));
        return template;
    }
}
