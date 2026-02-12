package com.secretsanta.infrastructure.kafka;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.events.UserCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaServiceBusTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private KafkaServiceBus serviceBus;

    @BeforeEach
    void setUp() {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3,
                Map.of(Exception.class, true, IllegalArgumentException.class, false), true));
        serviceBus = new KafkaServiceBus(kafkaTemplate, objectMapper, retryTemplate);
    }

    @Test
    void dispatches_command_to_registered_handler() throws Exception {
        CreateUserCommand command = CreateUserCommand.builder()
                .email("test@example.com")
                .name("Test User")
                .password("password123")
                .build();
        command.initDefaults("CREATE_USER");

        String json = "{\"commandType\":\"CREATE_USER\"}";
        when(objectMapper.readValue(json, BaseCommand.class)).thenReturn(command);

        AtomicInteger callCount = new AtomicInteger(0);
        serviceBus.registerCommandHandler(CreateUserCommand.class, cmd -> {
            callCount.incrementAndGet();
            assertThat(cmd.getEmail()).isEqualTo("test@example.com");
        });

        serviceBus.handleCommandMessage(json);

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void logs_warning_for_unregistered_command() throws Exception {
        CreateUserCommand command = CreateUserCommand.builder()
                .email("test@example.com")
                .name("Test")
                .password("password123")
                .build();
        command.initDefaults("CREATE_USER");

        String json = "{\"commandType\":\"CREATE_USER\"}";
        when(objectMapper.readValue(json, BaseCommand.class)).thenReturn(command);

        serviceBus.handleCommandMessage(json);
    }

    @Test
    void dispatches_event_to_registered_handler() throws Exception {
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId("user-123")
                .email("test@example.com")
                .name("Test User")
                .build();
        event.initDefaults("USER_CREATED");

        String json = "{\"eventType\":\"USER_CREATED\"}";
        when(objectMapper.readValue(json, BaseEvent.class)).thenReturn(event);

        AtomicInteger callCount = new AtomicInteger(0);
        serviceBus.registerEventHandler(UserCreatedEvent.class, evt -> {
            callCount.incrementAndGet();
            assertThat(evt.getUserId()).isEqualTo("user-123");
        });

        serviceBus.handleEventMessage(json);

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void logs_warning_for_unregistered_event() throws Exception {
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId("user-123")
                .email("test@example.com")
                .name("Test User")
                .build();
        event.initDefaults("USER_CREATED");

        String json = "{\"eventType\":\"USER_CREATED\"}";
        when(objectMapper.readValue(json, BaseEvent.class)).thenReturn(event);

        serviceBus.handleEventMessage(json);
    }

    @Test
    void emitEvent_publishes_serialized_json() throws Exception {
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId("user-123")
                .email("test@example.com")
                .name("Test User")
                .build();
        event.initDefaults("USER_CREATED");

        String serialized = "{\"eventType\":\"USER_CREATED\",\"userId\":\"user-123\"}";
        when(objectMapper.writeValueAsString(event)).thenReturn(serialized);

        serviceBus.emitEvent("user.events", "user-123", event);

        verify(kafkaTemplate).send("user.events", "user-123", serialized);
    }

    @Test
    void emitCommand_publishes_serialized_json() throws Exception {
        CreateUserCommand command = CreateUserCommand.builder()
                .email("test@example.com")
                .name("Test User")
                .password("password123")
                .build();
        command.initDefaults("CREATE_USER");

        String serialized = "{\"commandType\":\"CREATE_USER\",\"email\":\"test@example.com\"}";
        when(objectMapper.writeValueAsString(command)).thenReturn(serialized);

        serviceBus.emitCommand("user.commands", "cmd-key", command);

        verify(kafkaTemplate).send("user.commands", "cmd-key", serialized);
    }

    @Test
    void does_not_retry_business_rule_violation() throws Exception {
        CreateUserCommand command = CreateUserCommand.builder()
                .email("test@example.com")
                .name("Test")
                .password("password123")
                .build();
        command.initDefaults("CREATE_USER");

        String json = "{\"commandType\":\"CREATE_USER\"}";
        when(objectMapper.readValue(json, BaseCommand.class)).thenReturn(command);

        AtomicInteger callCount = new AtomicInteger(0);
        serviceBus.registerCommandHandler(CreateUserCommand.class, cmd -> {
            callCount.incrementAndGet();
            throw new IllegalArgumentException("Email already registered");
        });

        serviceBus.handleCommandMessage(json);

        assertThat(callCount.get()).isEqualTo(1);
    }

    // --- Retry: transient failure then success ---

    @Test
    void retries_transient_failure_then_succeeds() throws Exception {
        CreateUserCommand command = CreateUserCommand.builder()
                .email("test@example.com")
                .name("Test")
                .password("password123")
                .build();
        command.initDefaults("CREATE_USER");

        String json = "{\"commandType\":\"CREATE_USER\"}";
        when(objectMapper.readValue(json, BaseCommand.class)).thenReturn(command);

        AtomicInteger callCount = new AtomicInteger(0);
        serviceBus.registerCommandHandler(CreateUserCommand.class, cmd -> {
            if (callCount.incrementAndGet() == 1) {
                throw new RuntimeException("Transient DB failure");
            }
        });

        serviceBus.handleCommandMessage(json);

        assertThat(callCount.get()).isEqualTo(2);
    }

    // --- Retry: exhausted ---

    @Test
    void exhausts_retries_and_logs_error() throws Exception {
        CreateUserCommand command = CreateUserCommand.builder()
                .email("test@example.com")
                .name("Test")
                .password("password123")
                .build();
        command.initDefaults("CREATE_USER");

        String json = "{\"commandType\":\"CREATE_USER\"}";
        when(objectMapper.readValue(json, BaseCommand.class)).thenReturn(command);

        AtomicInteger callCount = new AtomicInteger(0);
        serviceBus.registerCommandHandler(CreateUserCommand.class, cmd -> {
            callCount.incrementAndGet();
            throw new RuntimeException("Persistent failure");
        });

        serviceBus.handleCommandMessage(json);

        assertThat(callCount.get()).isEqualTo(3);
    }

    // --- Deserialization error ---

    @Test
    void deserialization_error_does_not_retry() throws Exception {
        String badJson = "not-json";
        when(objectMapper.readValue(badJson, BaseCommand.class))
                .thenThrow(new RuntimeException("Deserialization failed"));

        // Should not throw — error is caught and logged
        serviceBus.handleCommandMessage(badJson);

        // No handler should be invoked
        verifyNoInteractions(kafkaTemplate);
    }
}
