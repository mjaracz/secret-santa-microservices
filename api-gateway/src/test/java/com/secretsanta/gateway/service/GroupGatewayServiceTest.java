package com.secretsanta.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.group.commands.AddMemberCommand;
import com.secretsanta.common.group.commands.UpdateGroupCommand;
import com.secretsanta.gateway.dto.AddMemberRequest;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.UpdateGroupRequest;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class GroupGatewayServiceTest {

    private static final String GROUP_TOPIC = "group.commands";

    @Mock
    private CommandDispatcher dispatcher;

    private GroupGatewayService groupGatewayService;

    @BeforeEach
    void setUp() {
        groupGatewayService = new GroupGatewayService(dispatcher);
        ReflectionTestUtils.setField(groupGatewayService, "groupCommandsTopic", GROUP_TOPIC);
    }

    @Test
    void forwardsRequesterWhenUpdatingGroup() {
        UpdateGroupRequest request = new UpdateGroupRequest(
                "owner-001", "Friends", "Updated", 12);
        ArgumentCaptor<BaseCommand> commandCaptor = ArgumentCaptor.forClass(BaseCommand.class);
        when(dispatcher.send(eq(GROUP_TOPIC), commandCaptor.capture(), eq("UPDATE_GROUP")))
                .thenReturn(Mono.just(CommandResponse.builder().success(true).build()));

        groupGatewayService.updateGroup("group-001", request).block();

        UpdateGroupCommand command = (UpdateGroupCommand) commandCaptor.getValue();
        assertThat(command.getRequestedBy()).isEqualTo("owner-001");
        assertThat(command.getName()).isEqualTo("Friends");
        assertThat(command.getDescription()).isEqualTo("Updated");
        assertThat(command.getMaxMembers()).isEqualTo(12);
        assertThat(command.getCommandType()).isEqualTo("UPDATE_GROUP");
    }

    @Test
    void forwardsRequesterWhenAddingMember() {
        AddMemberRequest request = new AddMemberRequest(
                "owner-001", "user-002", "user@example.com", "Jane Doe", "member");
        ArgumentCaptor<BaseCommand> commandCaptor = ArgumentCaptor.forClass(BaseCommand.class);
        when(dispatcher.send(eq(GROUP_TOPIC), commandCaptor.capture(), eq("ADD_MEMBER")))
                .thenReturn(Mono.just(CommandResponse.builder().success(true).build()));

        groupGatewayService.addMember("group-001", request).block();

        AddMemberCommand command = (AddMemberCommand) commandCaptor.getValue();
        assertThat(command.getRequestedBy()).isEqualTo("owner-001");
        assertThat(command.getUserId()).isEqualTo("user-002");
        assertThat(command.getRole()).isEqualTo("member");
        assertThat(command.getCommandType()).isEqualTo("ADD_MEMBER");
        verify(dispatcher).send(GROUP_TOPIC, command, "ADD_MEMBER");
    }
}
