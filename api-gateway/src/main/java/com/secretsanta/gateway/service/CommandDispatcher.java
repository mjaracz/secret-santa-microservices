package com.secretsanta.gateway.service;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.CommandFailedEvent;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

import static com.secretsanta.gateway.dto.CommandResponse.REQUEST_TIMEOUT_ERROR_CODE;

@Component
@RequiredArgsConstructor
public class CommandDispatcher {

    private final KafkaServiceBus serviceBus;

    public Mono<CommandResponse> send(
            String topic,
            BaseCommand command,
            String commandType
    ) {
        return Mono.fromCompletionStage(
                        serviceBus.sendAndReceive(
                                topic,
                                command.getCommandId(),
                                command
                        )
                )
                .map(event ->
                        mapResponse(
                                event,
                                command.getCommandId()
                        )
                )
                .onErrorResume(
                        TimeoutException.class,
                        exception -> Mono.just(
                                CommandResponse.failure(
                                        command.getCommandId(),
                                        REQUEST_TIMEOUT_ERROR_CODE,
                                        "Request timed out",
                                        commandType
                                )
                        )
                );
    }

    private CommandResponse mapResponse(
            BaseEvent event,
            String commandId
    ) {
        if (event instanceof CommandFailedEvent failed) {
            return CommandResponse.failure(
                    commandId,
                    failed.getErrorCode(),
                    failed.getReason(),
                    failed.getOriginalCommandType()
            );
        }

        return CommandResponse.success(commandId, event);
    }
}
