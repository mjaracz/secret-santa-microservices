package com.secretsanta.gateway.controller;

import com.secretsanta.common.group.events.GroupCreatedEvent;
import com.secretsanta.gateway.dto.AddMemberRequest;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateGroupRequest;
import com.secretsanta.gateway.dto.UpdateGroupRequest;
import com.secretsanta.gateway.security.AuthenticatedActor;
import com.secretsanta.gateway.service.GroupGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = GroupController.class, properties = "server.port=0")
@Import(TestSecurityConfiguration.class)
class GroupControllerTest {

    private static final String GROUP_ID = "7ed76f86-4c54-4c9d-8c53-82e0914fb01f";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GroupGatewayService groupGatewayService;

    @Test
    void returnsCreatedAndUsesAuthenticatedJwtActor() {
        GroupGatewayService directGatewayService = mock(GroupGatewayService.class);
        GroupController controller = new GroupController(directGatewayService);
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
        when(directGatewayService.createGroup(request, actor))
                .thenReturn(Mono.just(response));

        StepVerifier.create(controller.createGroup(request, jwt))
                .assertNext(result -> {
                    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(result.getBody()).isSameAs(response);
                })
                .verifyComplete();
    }

    @Test
    void rejectsInvalidCreateGroupRequest() {
        CreateGroupRequest request = new CreateGroupRequest(" ", "description", 2);

        webTestClient.post()
                .uri("/api/groups")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(groupGatewayService);
    }

    @Test
    void rejectsUpdateWithoutAnyFields() {
        UpdateGroupRequest request = new UpdateGroupRequest(null, null, null);

        webTestClient.put()
                .uri("/api/groups/{groupId}", GROUP_ID)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(groupGatewayService);
    }

    @Test
    void rejectsInvalidGroupIdBeforeDispatchingCommand() {
        UpdateGroupRequest request = new UpdateGroupRequest("Updated name", null, null);

        webTestClient.put()
                .uri("/api/groups/not-a-uuid")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(groupGatewayService);
    }

    @Test
    void rejectsInvalidMemberData() {
        AddMemberRequest request = new AddMemberRequest("", "invalid-email", "x", "OWNER");

        webTestClient.post()
                .uri("/api/groups/{groupId}/members", GROUP_ID)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(groupGatewayService);
    }

    @Test
    void rejectsInvalidGroupIdForDraw() {
        webTestClient.post()
                .uri("/api/groups/not-a-uuid/draw")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(groupGatewayService);
    }
}
