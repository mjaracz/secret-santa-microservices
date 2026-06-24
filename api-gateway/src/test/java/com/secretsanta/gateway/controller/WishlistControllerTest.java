package com.secretsanta.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import com.secretsanta.common.wishlist.dto.WishlistItemDto;
import com.secretsanta.common.wishlist.events.WishlistItemAddedEvent;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateWishlistItemRequest;
import com.secretsanta.gateway.security.AuthenticatedActor;
import com.secretsanta.gateway.service.WishlistGatewayService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class WishlistControllerTest {

    @Test
    void returnsCreatedAndUsesAuthenticatedJwtActor() {
        WishlistGatewayService wishlistGatewayService = mock(WishlistGatewayService.class);
        WishlistController controller = new WishlistController(wishlistGatewayService);
        CreateWishlistItemRequest request = new CreateWishlistItemRequest(
                "Headphones",
                "Noise cancelling",
                "https://example.com"
        );
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject("giver-001")
                .claim("roles", List.of("USER"))
                .build();
        AuthenticatedActor actor = AuthenticatedActor.from(jwt);
        WishlistItemAddedEvent event = WishlistItemAddedEvent.builder()
                .item(WishlistItemDto.builder()
                        .id("item-001")
                        .groupId("11111111-1111-1111-1111-111111111111")
                        .ownerUserId("giver-001")
                        .title("Headphones")
                        .build())
                .build();
        event.initDefaults("WISHLIST_ITEM_ADDED");
        CommandResponse response = CommandResponse.success("command-001", event);
        when(wishlistGatewayService.addItem(
                "11111111-1111-1111-1111-111111111111",
                request,
                actor
        )).thenReturn(Mono.just(response));

        StepVerifier.create(controller.addItem(
                        "11111111-1111-1111-1111-111111111111",
                        request,
                        jwt
                ))
                .assertNext(result -> {
                    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(result.getBody()).isSameAs(response);
                })
                .verifyComplete();
    }
}
