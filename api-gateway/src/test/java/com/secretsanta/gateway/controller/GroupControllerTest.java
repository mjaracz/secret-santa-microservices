package com.secretsanta.gateway.controller;

import com.secretsanta.common.group.events.GroupCreatedEvent;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateGroupRequest;
import com.secretsanta.gateway.security.AuthenticatedActor;
import com.secretsanta.gateway.service.GroupGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupControllerTest {

    @Test
    void returnsCreatedAndUsesAuthenticatedJwtActor() {
        GroupGatewayService groupGatewayService = mock(GroupGatewayService.class);
        GroupController controller = new GroupController(groupGatewayService);
        CreateGroupRequest request = new CreateGroupRequest(
                "Family",
                "Christmas draw",
                8
        );
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject("owner-001")
                .claim("roles", List.of("USER"))
                .build();
        AuthenticatedActor actor = AuthenticatedActor.from(jwt);
        GroupCreatedEvent event = GroupCreatedEvent.builder()
                .groupId("group-001")
                .name("Family")
                .ownerId("owner-001")
                .maxMembers(8)
                .build();
        event.initDefaults("GROUP_CREATED");
        CommandResponse response = CommandResponse.success("command-001", event);
        when(groupGatewayService.createGroup(request, actor))
                .thenReturn(Mono.just(response));

        StepVerifier.create(controller.createGroup(request, jwt))
                .assertNext(result -> {
                    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(result.getBody()).isSameAs(response);
                })
                .verifyComplete();
    }
}
