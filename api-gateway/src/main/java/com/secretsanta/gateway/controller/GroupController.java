package com.secretsanta.gateway.controller;

import com.secretsanta.common.group.commands.AddMemberCommand;
import com.secretsanta.common.group.commands.CreateGroupCommand;
import com.secretsanta.common.group.commands.DeleteGroupCommand;
import com.secretsanta.common.group.commands.UpdateGroupCommand;
import com.secretsanta.gateway.dto.AddMemberRequest;
import com.secretsanta.gateway.dto.CommandAcceptedResponse;
import com.secretsanta.gateway.dto.CreateGroupRequest;
import com.secretsanta.gateway.dto.UpdateGroupRequest;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final KafkaServiceBus serviceBus;

    @Value("${kafka.topics.group-commands}")
    private String groupCommandsTopic;

    @PostMapping
    public Mono<ResponseEntity<CommandAcceptedResponse>> createGroup(
            @RequestBody CreateGroupRequest request) {
        CreateGroupCommand command = CreateGroupCommand.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(request.getOwnerId())
                .maxMembers(request.getMaxMembers())
                .build();
        command.initDefaults("CREATE_GROUP");

        serviceBus.emitCommand(groupCommandsTopic, command.getCommandId(), command);

        return Mono.just(ResponseEntity.accepted()
                .body(new CommandAcceptedResponse(command.getCommandId(),
                        "Group creation command accepted")));
    }

    @PutMapping("/{groupId}")
    public Mono<ResponseEntity<CommandAcceptedResponse>> updateGroup(
            @PathVariable String groupId,
            @RequestBody UpdateGroupRequest request) {
        UpdateGroupCommand command = UpdateGroupCommand.builder()
                .groupId(groupId)
                .name(request.getName())
                .description(request.getDescription())
                .maxMembers(request.getMaxMembers())
                .build();
        command.initDefaults("UPDATE_GROUP");

        serviceBus.emitCommand(groupCommandsTopic, command.getCommandId(), command);

        return Mono.just(ResponseEntity.accepted()
                .body(new CommandAcceptedResponse(command.getCommandId(),
                        "Group update command accepted")));
    }

    @DeleteMapping("/{groupId}")
    public Mono<ResponseEntity<CommandAcceptedResponse>> deleteGroup(
            @PathVariable String groupId,
            @RequestParam String ownerId) {
        DeleteGroupCommand command = DeleteGroupCommand.builder()
                .groupId(groupId)
                .ownerId(ownerId)
                .build();
        command.initDefaults("DELETE_GROUP");

        serviceBus.emitCommand(groupCommandsTopic, command.getCommandId(), command);

        return Mono.just(ResponseEntity.accepted()
                .body(new CommandAcceptedResponse(command.getCommandId(),
                        "Group deletion command accepted")));
    }

    @PostMapping("/{groupId}/members")
    public Mono<ResponseEntity<CommandAcceptedResponse>> addMember(
            @PathVariable String groupId,
            @RequestBody AddMemberRequest request) {
        AddMemberCommand command = AddMemberCommand.builder()
                .groupId(groupId)
                .userId(request.getUserId())
                .userEmail(request.getUserEmail())
                .userName(request.getUserName())
                .role(request.getRole())
                .build();
        command.initDefaults("ADD_MEMBER");

        serviceBus.emitCommand(groupCommandsTopic, command.getCommandId(), command);

        return Mono.just(ResponseEntity.accepted()
                .body(new CommandAcceptedResponse(command.getCommandId(),
                        "Add member command accepted")));
    }
}
