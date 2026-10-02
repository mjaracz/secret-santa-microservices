package com.secretsanta.gateway.service;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.group.commands.GetMyGroupsCommand;
import com.secretsanta.gateway.dto.CommandResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupGatewayServiceTest {

    @Mock
    private CommandDispatcher dispatcher;

    private GroupGatewayService groupGatewayService;

    @BeforeEach
    void setUp() {
        groupGatewayService = new GroupGatewayService(dispatcher);
        ReflectionTestUtils.setField(
                groupGatewayService,
                "groupCommandsTopic",
                "group.commands"
        );
        when(dispatcher.send(
                eq("group.commands"),
                any(BaseCommand.class),
                eq("GET_MY_GROUPS")
        )).thenReturn(Mono.just(CommandResponse.success("command-1", null)));
    }

    @Test
    void sendsGetMyGroupsCommandWithRequesterFromJwtSubject() {
        groupGatewayService.getMyGroups("user-from-jwt").block();

        ArgumentCaptor<BaseCommand> commandCaptor = ArgumentCaptor.forClass(BaseCommand.class);
        verify(dispatcher).send(
                eq("group.commands"),
                commandCaptor.capture(),
                eq("GET_MY_GROUPS")
        );

        GetMyGroupsCommand command = (GetMyGroupsCommand) commandCaptor.getValue();
        assertThat(command.getRequestedBy()).isEqualTo("user-from-jwt");
        assertThat(command.getCommandId()).isNotBlank();
        assertThat(command.getCommandType()).isEqualTo("GET_MY_GROUPS");
    }
}
