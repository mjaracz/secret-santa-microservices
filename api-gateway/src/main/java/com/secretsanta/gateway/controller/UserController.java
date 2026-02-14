package com.secretsanta.gateway.controller;

import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.gateway.dto.CommandAcceptedResponse;
import com.secretsanta.gateway.dto.CreateUserRequest;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

        private final KafkaServiceBus serviceBus;

        @Value("${kafka.topics.user-commands}")
        private String userCommandsTopic;

        @PostMapping
        public Mono<ResponseEntity<CommandAcceptedResponse>> createUser(
                        @RequestBody CreateUserRequest request) {
                CreateUserCommand command = CreateUserCommand.builder()
                                .email(request.getEmail())
                                .name(request.getName())
                                .password(request.getPassword())
                                .build();
                command.initDefaults("CREATE_USER");

                serviceBus.emitCommand(userCommandsTopic, command.getCommandId(), command);

                return Mono.just(ResponseEntity.accepted()
                                .body(new CommandAcceptedResponse(command.getCommandId(),
                                                String.format("CreateUserCommand successfully created user with email %s",
                                                                request.getEmail()))));
        }
}
