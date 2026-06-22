package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.AddMemberRequest;
import com.secretsanta.gateway.dto.CreateGroupRequest;
import com.secretsanta.gateway.dto.DrawNamesRequest;
import com.secretsanta.gateway.dto.UpdateGroupRequest;
import com.secretsanta.gateway.service.GroupGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.verifyNoInteractions;

@WebFluxTest(controllers = GroupController.class, properties = "server.port=0")
class GroupControllerTest {

    private static final String GROUP_ID = "7ed76f86-4c54-4c9d-8c53-82e0914fb01f";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GroupGatewayService groupGatewayService;

    @Test
    void rejectsInvalidCreateGroupRequest() {
        CreateGroupRequest request = new CreateGroupRequest(" ", "description", "", 2);

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
    void rejectsBlankDrawRequester() {
        webTestClient.post()
                .uri("/api/groups/{groupId}/draw", GROUP_ID)
                .bodyValue(new DrawNamesRequest(" "))
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(groupGatewayService);
    }

    @Test
    void rejectsBlankOwnerOnDelete() {
        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/groups/{groupId}")
                        .queryParam("ownerId", " ")
                        .build(GROUP_ID))
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(groupGatewayService);
    }
}
