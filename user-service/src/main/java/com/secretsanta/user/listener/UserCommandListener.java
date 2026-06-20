package com.secretsanta.user.listener;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.CommandFailedEvent;
import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.user.exception.UserCommandException;
import com.secretsanta.user.service.UserService;
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
    private final CreateUserCommandValidator commandValidator;

    @Value("${kafka.topics.user-events}")
    private String userEventsTopic;

    public UserCommandListener(
            KafkaServiceBus serviceBus,
            UserService userService,
            CreateUserCommandValidator commandValidator
    ) {
        this.serviceBus = serviceBus;
        this.userService = userService;
        this.commandValidator = commandValidator;

        serviceBus.registerCommandHandler(
                CreateUserCommand.class,
                this::onCreateUser
        );
    }

    @KafkaListener(
            topics = "${kafka.topics.user-commands}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listen(String message) {
        serviceBus.handleCommandMessage(message, this::emitFailure);
    }

    private void onCreateUser(CreateUserCommand command) {
        try {
            commandValidator.validate(command);

            UserCreatedEvent event = userService.createUser(command);
            event.setCorrelationId(command.getCommandId());

            serviceBus.emitEvent(
                    userEventsTopic,
                    event.getUserId(),
                    event
            );
        } catch (UserCommandException exception) {
            emitFailure(
                    command,
                    exception.getErrorCode(),
                    exception.getMessage()
            );
        }
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
