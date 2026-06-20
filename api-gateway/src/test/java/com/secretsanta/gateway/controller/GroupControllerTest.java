package com.secretsanta.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.secretsanta.common.group.events.GroupCreatedEvent;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateGroupRequest;
import com.secretsanta.gateway.service.GroupGatewayService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GroupControllerTest {

    @Test
    void returnsCreatedWhenGroupIsCreated() {
        GroupGatewayService groupGatewayService = mock(GroupGatewayService.class);
        GroupController controller = new GroupController(groupGatewayService);
        CreateGroupRequest request = new CreateGroupRequest(
                "Family", "Christmas draw", "owner-001", 8);
        GroupCreatedEvent event = GroupCreatedEvent.builder()
                .groupId("group-001")
                .name("Family")
                .ownerId("owner-001")
                .maxMembers(8)
                .build();
        event.initDefaults("GROUP_CREATED");
        CommandResponse response = CommandResponse.success("command-001", event);
        when(groupGatewayService.createGroup(request)).thenReturn(Mono.just(response));

        StepVerifier.create(controller.createGroup(request))
                .assertNext(result -> {
                    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(result.getBody()).isSameAs(response);
                })
                .verifyComplete();
    }
}
