package com.secretsanta.user.listener;

import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.CommandFailedEvent;
import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.dto.AuthenticatedUserDto;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.user.exception.UserCommandException;
import com.secretsanta.user.service.UserService;
import com.secretsanta.user.service.UserAuthenticationService;
import com.secretsanta.user.service.EmailVerificationService;
import com.secretsanta.user.service.UserRegistrationResult;
import com.secretsanta.user.validator.AuthenticationCommandValidator;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private UserAuthenticationService authenticationService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private CreateUserCommandValidator commandValidator;

    @Mock
    private AuthenticationCommandValidator authenticationCommandValidator;

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
                        3,
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
                authenticationService,
                emailVerificationService,
                commandValidator,
                authenticationCommandValidator
        );

        ReflectionTestUtils.setField(
                listener,
                "userEventsTopic",
                "user.events"
        );
        ReflectionTestUtils.setField(
                listener,
                "notificationEventsTopic",
                "notification.events"
        );
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

        EmailVerificationRequestedEvent verificationEvent =
                EmailVerificationRequestedEvent.builder()
                        .userId("user-123")
                        .email("user@example.com")
                        .name("New User")
                        .verificationToken("verification-token")
                        .build();
        verificationEvent.initDefaults("EMAIL_VERIFICATION_REQUESTED");

        when(userService.createUser(any(CreateUserCommand.class)))
                .thenReturn(new UserRegistrationResult(
                        serviceEvent,
                        verificationEvent
                ));

        listener.listen(objectMapper.writeValueAsString(command));

        verify(commandValidator).validate(any(CreateUserCommand.class));
        verify(userService).createUser(any(CreateUserCommand.class));
        verify(kafkaTemplate).send(
                eq("user.events"),
                eq("user-123"),
                jsonCaptor.capture()
        );
        verify(kafkaTemplate).send(
                eq("notification.events"),
                eq("user-123"),
                any(String.class)
        );

        BaseEvent publishedEvent = readEvent(jsonCaptor.getValue());

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
    void unexpectedExceptionIsRetriedAndEmitsInternalError() throws Exception {
        CreateUserCommand command = validCommand();

        when(userService.createUser(any(CreateUserCommand.class)))
                .thenThrow(new RuntimeException("Database unavailable"));

        listener.listen(objectMapper.writeValueAsString(command));

        verify(userService, times(3))
                .createUser(any(CreateUserCommand.class));
        verify(commandValidator, times(3))
                .validate(any(CreateUserCommand.class));

        CommandFailedEvent failedEvent = captureFailureEvent(command);
        assertThat(failedEvent.getErrorCode())
                .isEqualTo("INTERNAL_ERROR");
        assertThat(failedEvent.getReason())
                .isEqualTo("Internal error while processing command")
                .doesNotContain("Database unavailable");
    }

    @Test
    void authenticatesUserAndPreservesCorrelationId() throws Exception {
        AuthenticateUserCommand command = AuthenticateUserCommand.builder()
                .email("user@example.com")
                .password("correct-horse-battery-staple")
                .refreshTokenHash("a".repeat(64))
                .build();
        command.initDefaults("AUTHENTICATE_USER");
        UserAuthenticatedEvent serviceEvent = UserAuthenticatedEvent.builder()
                .user(AuthenticatedUserDto.builder()
                        .userId("user-123")
                        .email("user@example.com")
                        .name("User")
                        .role(UserRole.USER)
                        .build())
                .refreshTokenExpiresAt(1_800_000_000_000L)
                .build();
        serviceEvent.initDefaults("USER_AUTHENTICATED");
        when(authenticationService.authenticate(any(AuthenticateUserCommand.class)))
                .thenReturn(serviceEvent);

        listener.listen(objectMapper.writeValueAsString(command));

        verify(authenticationCommandValidator).validate(command);
        verify(authenticationService).authenticate(command);
        verify(kafkaTemplate).send(
                eq("user.events"),
                eq(command.getCommandId()),
                jsonCaptor.capture()
        );
        BaseEvent published = readEvent(jsonCaptor.getValue());
        assertThat(published).isInstanceOf(UserAuthenticatedEvent.class);
        assertThat(published.getCorrelationId())
                .isEqualTo(command.getCommandId());
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

    private BaseEvent readEvent(String json) {
        try {
            return objectMapper.readValue(json, BaseEvent.class);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
