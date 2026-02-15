package com.secretsanta.gateway.service;

import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserGatewayService {

    private final CommandDispatcher dispatcher;

    @Value("${kafka.topics.user-commands}")
    private String userCommandsTopic;

    public Mono<CommandResponse> createUser(CreateUserRequest request) {
        CreateUserCommand command = CreateUserCommand.builder()
                .email(request.getEmail())
                .name(request.getName())
                .password(request.getPassword())
                .build();
        command.initDefaults("CREATE_USER");

        return dispatcher.send(userCommandsTopic, command, "CREATE_USER");
    }
}
