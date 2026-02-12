package com.secretsanta.user.listener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.user.service.UserService;

@Component
public class UserCommandListener {

    private final KafkaServiceBus serviceBus;
    private final UserService userService;

    @Value("${kafka.topics.user-events}")
    private String userEventsTopic;

    public UserCommandListener(KafkaServiceBus serviceBus, UserService userService) {
        this.serviceBus = serviceBus;
        this.userService = userService;
        serviceBus.registerCommandHandler(CreateUserCommand.class, this::onCreateUser);
    }

    @KafkaListener(
            topics = "${kafka.topics.user-commands}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void listen(String message) {
        serviceBus.handleCommandMessage(message);
    }

    private void onCreateUser(CreateUserCommand command) {
        UserCreatedEvent event = userService.createUser(command);
        serviceBus.emitEvent(userEventsTopic, event.getUserId(), event);
    }
}
