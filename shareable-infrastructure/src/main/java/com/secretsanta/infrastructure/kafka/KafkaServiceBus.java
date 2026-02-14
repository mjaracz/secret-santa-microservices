package com.secretsanta.infrastructure.kafka;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class KafkaServiceBus {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;
    private final Map<Class<? extends BaseCommand>, CommandHandlerEntry<?>> commandHandlers = new HashMap<>();
    private final Map<Class<? extends BaseEvent>, EventHandlerEntry<?>> eventHandlers = new HashMap<>();

    public KafkaServiceBus(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this(kafkaTemplate, objectMapper, buildDefaultRetryTemplate());
    }

    public KafkaServiceBus(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
            RetryTemplate retryTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;
    }

    @FunctionalInterface
    public interface CommandHandler<T extends BaseCommand> {
        void handle(T command);
    }

    @FunctionalInterface
    public interface EventHandler<T extends BaseEvent> {
        void handle(T event);
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
        try {
            BaseCommand command = objectMapper.readValue(json, BaseCommand.class);
            log.info("Received command: type={}, id={}", command.getCommandType(), command.getCommandId());
            retryTemplate.execute(context -> {
                dispatchCommand(command);
                return null;
            });
        } catch (IllegalArgumentException e) {
            log.error("Business rule violation: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing command after retries exhausted", e);
        }
    }

    public void handleEventMessage(String json) {
        try {
            BaseEvent event = objectMapper.readValue(json, BaseEvent.class);
            log.info("Received event: type={}, id={}", event.getEventType(), event.getEventId());
            retryTemplate.execute(context -> {
                dispatchEvent(event);
                return null;
            });
        } catch (IllegalArgumentException e) {
            log.error("Business rule violation: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing event after retries exhausted", e);
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

    private void dispatchCommand(BaseCommand command) {
        CommandHandlerEntry<?> entry = commandHandlers.get(command.getClass());
        if (entry != null) {
            entry.dispatch(command);
        } else {
            log.warn("No handler registered for command type: {}", command.getClass().getSimpleName());
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
            kafkaTemplate.send(topic, key, json);
            log.info("Published message: key={}, topic={}", key, topic);
        } catch (Exception e) {
            log.error("Error publishing message: key={}, topic={}", key, topic, e);
        }
    }

    private static RetryTemplate buildDefaultRetryTemplate() {
        RetryTemplate template = new RetryTemplate();
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(100L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(1000L);
        template.setBackOffPolicy(backOff);
        template.setRetryPolicy(new SimpleRetryPolicy(3,
                Map.of(Exception.class, true, IllegalArgumentException.class, false), true));
        return template;
    }
}
