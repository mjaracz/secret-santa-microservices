package com.secretsanta.user.listener;

import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.CommandFailedEvent;
import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.user.exception.UserCommandException;
import com.secretsanta.user.service.UserService;
import com.secretsanta.user.validator.CreateUserCommandValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCommandListenerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private UserService userService;

    @Mock
    private CreateUserCommandValidator commandValidator;

    @Captor
    private ArgumentCaptor<String> jsonCaptor;

    private ObjectMapper objectMapper;
    private UserCommandListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        retryTemplate.setRetryPolicy(
                new SimpleRetryPolicy(
                        1,
                        Map.of(
                                Exception.class,
                                true,
                                IllegalArgumentException.class,
                                false
                        ),
                        true
                )
        );

        KafkaServiceBus serviceBus = new KafkaServiceBus(
                kafkaTemplate,
                objectMapper,
                retryTemplate
        );

        listener = new UserCommandListener(
                serviceBus,
                userService,
                commandValidator
        );

        ReflectionTestUtils.setField(
                listener,
                "userEventsTopic",
                "user.events"
        );

        lenient().when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void emitsCreatedEventWithStatusAndCorrelationId() throws Exception {
        CreateUserCommand command = validCommand();

        UserCreatedEvent serviceEvent = UserCreatedEvent.builder()
                .userId("user-123")
                .email("user@example.com")
                .name("New User")
                .status(UserAccountStatus.PENDING_VERIFICATION)
                .build();
        serviceEvent.initDefaults("USER_CREATED");

        when(userService.createUser(any(CreateUserCommand.class)))
                .thenReturn(serviceEvent);

        listener.listen(objectMapper.writeValueAsString(command));

        verify(commandValidator).validate(any(CreateUserCommand.class));
        verify(userService).createUser(any(CreateUserCommand.class));
        verify(kafkaTemplate).send(
                eq("user.events"),
                eq("user-123"),
                jsonCaptor.capture()
        );

        BaseEvent publishedEvent = objectMapper.readValue(
                jsonCaptor.getValue(),
                BaseEvent.class
        );

        assertThat(publishedEvent).isInstanceOf(UserCreatedEvent.class);

        UserCreatedEvent createdEvent = (UserCreatedEvent) publishedEvent;
        assertThat(createdEvent.getStatus())
                .isEqualTo(UserAccountStatus.PENDING_VERIFICATION);
        assertThat(createdEvent.getCorrelationId())
                .isEqualTo(command.getCommandId());
    }

    @Test
    void invalidCommandDoesNotInvokeUserService() throws Exception {
        CreateUserCommand command = validCommand();

        doThrow(
                new UserCommandException(
                        "USER_VALIDATION_FAILED",
                        "email: Invalid email format"
                )
        ).when(commandValidator).validate(any(CreateUserCommand.class));

        listener.listen(objectMapper.writeValueAsString(command));

        verifyNoInteractions(userService);

        CommandFailedEvent failedEvent = captureFailureEvent(command);
        assertThat(failedEvent.getErrorCode())
                .isEqualTo("USER_VALIDATION_FAILED");
        assertThat(failedEvent.getReason())
                .isEqualTo("email: Invalid email format");
    }

    @Test
    void duplicateEmailEmitsTypedFailureEvent() throws Exception {
        CreateUserCommand command = validCommand();

        when(userService.createUser(any(CreateUserCommand.class)))
                .thenThrow(
                        new UserCommandException(
                                "USER_EMAIL_ALREADY_EXISTS",
                                "Email is already registered"
                        )
                );

        listener.listen(objectMapper.writeValueAsString(command));

        verify(userService, times(1))
                .createUser(any(CreateUserCommand.class));

        CommandFailedEvent failedEvent = captureFailureEvent(command);
        assertThat(failedEvent.getErrorCode())
                .isEqualTo("USER_EMAIL_ALREADY_EXISTS");
        assertThat(failedEvent.getReason())
                .isEqualTo("Email is already registered");
        assertThat(failedEvent.getOriginalCommandType())
                .isEqualTo("CREATE_USER");
    }

    @Test
    void unexpectedExceptionIsPropagatedForKafkaRedelivery() throws Exception {
        CreateUserCommand command = validCommand();

        when(userService.createUser(any(CreateUserCommand.class)))
                .thenThrow(new RuntimeException("Database unavailable"));

        String message = objectMapper.writeValueAsString(command);
        assertThatThrownBy(() -> listener.listen(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database unavailable");

        verify(userService, times(1))
                .createUser(any(CreateUserCommand.class));
        verify(commandValidator, times(1))
                .validate(any(CreateUserCommand.class));

        verifyNoInteractions(kafkaTemplate);
    }

    private CommandFailedEvent captureFailureEvent(
            CreateUserCommand command
    ) throws Exception {
        verify(kafkaTemplate).send(
                eq("user.events"),
                eq(command.getCommandId()),
                jsonCaptor.capture()
        );

        BaseEvent event = objectMapper.readValue(
                jsonCaptor.getValue(),
                BaseEvent.class
        );

        assertThat(event).isInstanceOf(CommandFailedEvent.class);

        CommandFailedEvent failedEvent = (CommandFailedEvent) event;
        assertThat(failedEvent.getCorrelationId())
                .isEqualTo(command.getCommandId());

        return failedEvent;
    }

    private CreateUserCommand validCommand() {
        CreateUserCommand command = CreateUserCommand.builder()
                .email("user@example.com")
                .name("New User")
                .password("correct-horse-battery-staple")
                .build();
        command.initDefaults("CREATE_USER");
        return command;
    }
}
