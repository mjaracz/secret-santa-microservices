package com.secretsanta.user.listener;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.CommandFailedEvent;
import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.commands.RefreshSessionCommand;
import com.secretsanta.common.user.commands.ResendEmailVerificationCommand;
import com.secretsanta.common.user.commands.RevokeSessionCommand;
import com.secretsanta.common.user.commands.VerifyEmailCommand;
import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import com.secretsanta.common.user.events.EmailVerificationResentEvent;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.user.exception.UserCommandException;
import com.secretsanta.user.service.UserService;
import com.secretsanta.user.service.UserAuthenticationService;
import com.secretsanta.user.service.UserRegistrationResult;
import com.secretsanta.user.service.EmailVerificationResendResult;
import com.secretsanta.user.service.EmailVerificationService;
import com.secretsanta.user.validator.AuthenticationCommandValidator;
import com.secretsanta.user.validator.CreateUserCommandValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserCommandListener {

    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal error while processing command";

    private final KafkaServiceBus serviceBus;
    private final UserService userService;
    private final UserAuthenticationService authenticationService;
    private final EmailVerificationService emailVerificationService;
    private final CreateUserCommandValidator commandValidator;
    private final AuthenticationCommandValidator authenticationCommandValidator;

    @Value("${kafka.topics.user-events}")
    private String userEventsTopic;

    @Value("${kafka.topics.notification-events}")
    private String notificationEventsTopic;

    public UserCommandListener(
            KafkaServiceBus serviceBus,
            UserService userService,
            UserAuthenticationService authenticationService,
            EmailVerificationService emailVerificationService,
            CreateUserCommandValidator commandValidator,
            AuthenticationCommandValidator authenticationCommandValidator
    ) {
        this.serviceBus = serviceBus;
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.emailVerificationService = emailVerificationService;
        this.commandValidator = commandValidator;
        this.authenticationCommandValidator = authenticationCommandValidator;

        serviceBus.registerCommandHandler(
                CreateUserCommand.class,
                this::onCreateUser
        );
        serviceBus.registerCommandHandler(AuthenticateUserCommand.class, this::onAuthenticateUser);
        serviceBus.registerCommandHandler(RefreshSessionCommand.class, this::onRefreshSession);
        serviceBus.registerCommandHandler(RevokeSessionCommand.class, this::onRevokeSession);
        serviceBus.registerCommandHandler(VerifyEmailCommand.class, this::onVerifyEmail);
        serviceBus.registerCommandHandler(
                ResendEmailVerificationCommand.class,
                this::onResendEmailVerification
        );
    }

    @KafkaListener(
            topics = {
                    "${kafka.topics.user-commands}",
                    "${kafka.topics.auth-commands}"
            },
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listen(String message) {
        serviceBus.handleCommandMessage(message, this::emitFailure);
    }

    private void onCreateUser(CreateUserCommand command) {
        try {
            commandValidator.validate(command);

            UserRegistrationResult result = userService.createUser(command);
            UserCreatedEvent event = result.userCreatedEvent();
            event.setCorrelationId(command.getCommandId());

            serviceBus.emitEvent(
                    userEventsTopic,
                    event.getUserId(),
                    event
            );
            emitVerificationNotification(result.verificationRequestedEvent());
        } catch (UserCommandException exception) {
            emitFailure(
                    command,
                    exception.getErrorCode(),
                    exception.getMessage()
            );
        }
    }

    private void onAuthenticateUser(AuthenticateUserCommand command) {
        handleDomainCommand(command, () -> authenticationService.authenticate(command));
    }

    private void onRefreshSession(RefreshSessionCommand command) {
        handleDomainCommand(command, () -> authenticationService.refresh(command));
    }

    private void onRevokeSession(RevokeSessionCommand command) {
        handleDomainCommand(command, () -> authenticationService.revoke(command));
    }

    private void onVerifyEmail(VerifyEmailCommand command) {
        handleDomainCommand(command, () -> emailVerificationService.verify(command));
    }

    private void onResendEmailVerification(
            ResendEmailVerificationCommand command
    ) {
        try {
            authenticationCommandValidator.validate(command);
            EmailVerificationResendResult result = emailVerificationService.resend(command);
            EmailVerificationResentEvent response = result.responseEvent();
            emitReply(command, response, command.getCommandId());
            if (result.notificationEvent() != null) {
                emitVerificationNotification(result.notificationEvent());
            }
        } catch (UserCommandException exception) {
            emitFailure(command, exception.getErrorCode(), exception.getMessage());
        }
    }

    private void handleDomainCommand(
            BaseCommand command,
            java.util.function.Supplier<com.secretsanta.common.BaseEvent> handler
    ) {
        try {
            authenticationCommandValidator.validate(command);
            com.secretsanta.common.BaseEvent event = handler.get();
            emitReply(command, event, command.getCommandId());
        } catch (UserCommandException exception) {
            emitFailure(command, exception.getErrorCode(), exception.getMessage());
        }
    }

    private void emitReply(
            BaseCommand command,
            com.secretsanta.common.BaseEvent event,
            String key
    ) {
        event.setCorrelationId(command.getCommandId());
        serviceBus.emitEvent(userEventsTopic, key, event);
    }

    private void emitVerificationNotification(
            EmailVerificationRequestedEvent event
    ) {
        serviceBus.emitEvent(notificationEventsTopic, event.getUserId(), event);
    }

    private void emitFailure(BaseCommand command, String ignoredReason) {
        emitFailure(
                command,
                INTERNAL_ERROR_CODE,
                INTERNAL_ERROR_MESSAGE
        );
    }

    private void emitFailure(
            BaseCommand command,
            String errorCode,
            String reason
    ) {
        CommandFailedEvent failedEvent = CommandFailedEvent.builder()
                .correlationId(command.getCommandId())
                .errorCode(errorCode)
                .reason(reason)
                .originalCommandType(command.getCommandType())
                .build();

        failedEvent.initDefaults("COMMAND_FAILED");

        serviceBus.emitEvent(
                userEventsTopic,
                command.getCommandId(),
                failedEvent
        );
    }
}
