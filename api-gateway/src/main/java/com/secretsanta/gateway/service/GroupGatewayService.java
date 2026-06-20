package com.secretsanta.gateway.service;

import com.secretsanta.common.group.commands.AddMemberCommand;
import com.secretsanta.common.group.commands.CreateGroupCommand;
import com.secretsanta.common.group.commands.DeleteGroupCommand;
import com.secretsanta.common.group.commands.DrawNamesCommand;
import com.secretsanta.common.group.commands.UpdateGroupCommand;
import com.secretsanta.gateway.dto.AddMemberRequest;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateGroupRequest;
import com.secretsanta.gateway.dto.UpdateGroupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.secretsanta.gateway.security.AuthenticatedActor;

@Service
@RequiredArgsConstructor
public class GroupGatewayService {

    private final CommandDispatcher dispatcher;

    @Value("${kafka.topics.group-commands}")
    private String groupCommandsTopic;

    public Mono<CommandResponse> createGroup(
            CreateGroupRequest request,
            AuthenticatedActor actor
    ) {
        CreateGroupCommand command = CreateGroupCommand.builder()
                .name(request.getName())
                .description(request.getDescription())
                .maxMembers(request.getMaxMembers())
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("CREATE_GROUP");

        return dispatcher.send(groupCommandsTopic, command, "CREATE_GROUP");
    }

    public Mono<CommandResponse> updateGroup(
            String groupId,
            UpdateGroupRequest request,
            AuthenticatedActor actor
    ) {
        UpdateGroupCommand command = UpdateGroupCommand.builder()
                .groupId(groupId)
                .requestedBy(request.getRequestedBy())
                .name(request.getName())
                .description(request.getDescription())
                .maxMembers(request.getMaxMembers())
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("UPDATE_GROUP");

        return dispatcher.send(groupCommandsTopic, command, "UPDATE_GROUP");
    }

    public Mono<CommandResponse> deleteGroup(
            String groupId,
            AuthenticatedActor actor
    ) {
        DeleteGroupCommand command = DeleteGroupCommand.builder()
                .groupId(groupId)
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("DELETE_GROUP");

        return dispatcher.send(groupCommandsTopic, command, "DELETE_GROUP");
    }

    public Mono<CommandResponse> addMember(
            String groupId,
            AddMemberRequest request,
            AuthenticatedActor actor
    ) {
        AddMemberCommand command = AddMemberCommand.builder()
                .groupId(groupId)
                .requestedBy(request.getRequestedBy())
                .userId(request.getUserId())
                .userEmail(request.getUserEmail())
                .userName(request.getUserName())
                .role(request.getRole())
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("ADD_MEMBER");

        return dispatcher.send(groupCommandsTopic, command, "ADD_MEMBER");
    }

    public Mono<CommandResponse> drawNames(
            String groupId,
            AuthenticatedActor actor
    ) {
        DrawNamesCommand command = DrawNamesCommand.builder()
                .groupId(groupId)
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("DRAW_NAMES");

        return dispatcher.send(groupCommandsTopic, command, "DRAW_NAMES");
    }
}
