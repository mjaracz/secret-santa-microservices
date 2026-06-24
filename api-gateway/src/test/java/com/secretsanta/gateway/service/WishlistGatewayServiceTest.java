package com.secretsanta.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.common.wishlist.commands.AddWishlistItemCommand;
import com.secretsanta.common.wishlist.commands.GetReceiverWishlistCommand;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateWishlistItemRequest;
import com.secretsanta.gateway.security.AuthenticatedActor;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WishlistGatewayServiceTest {

    private static final String WISHLIST_TOPIC = "wishlist.commands";
    private static final AuthenticatedActor ACTOR = new AuthenticatedActor(
            "giver-001",
            Set.of(UserRole.USER)
    );

    @Mock
    private CommandDispatcher dispatcher;

    private WishlistGatewayService wishlistGatewayService;

    @BeforeEach
    void setUp() {
        wishlistGatewayService = new WishlistGatewayService(dispatcher);
        ReflectionTestUtils.setField(
                wishlistGatewayService,
                "wishlistCommandsTopic",
                WISHLIST_TOPIC
        );
    }

    @Test
    void buildsAddWishlistItemCommandFromAuthenticatedActor() {
        ArgumentCaptor<BaseCommand> commandCaptor = ArgumentCaptor.forClass(BaseCommand.class);
        when(dispatcher.send(eq(WISHLIST_TOPIC), commandCaptor.capture(), eq("ADD_WISHLIST_ITEM")))
                .thenReturn(Mono.just(CommandResponse.builder().success(true).build()));

        wishlistGatewayService.addItem(
                "11111111-1111-1111-1111-111111111111",
                new CreateWishlistItemRequest("Headphones", "Noise cancelling", "https://example.com"),
                ACTOR
        ).block();

        AddWishlistItemCommand command = (AddWishlistItemCommand) commandCaptor.getValue();
        assertThat(command.getActorId()).isEqualTo("giver-001");
        assertThat(command.getActorRoles()).containsExactly(UserRole.USER);
        assertThat(command.getTitle()).isEqualTo("Headphones");
        assertThat(command.getCommandType()).isEqualTo("ADD_WISHLIST_ITEM");
        verify(dispatcher).send(WISHLIST_TOPIC, command, "ADD_WISHLIST_ITEM");
    }

    @Test
    void buildsReceiverWishlistQueryCommandFromAuthenticatedActor() {
        ArgumentCaptor<BaseCommand> commandCaptor = ArgumentCaptor.forClass(BaseCommand.class);
        when(dispatcher.send(eq(WISHLIST_TOPIC), commandCaptor.capture(), eq("GET_RECEIVER_WISHLIST")))
                .thenReturn(Mono.just(CommandResponse.builder().success(true).build()));

        wishlistGatewayService.getReceiverWishlist(
                "11111111-1111-1111-1111-111111111111",
                ACTOR
        ).block();

        GetReceiverWishlistCommand command = (GetReceiverWishlistCommand) commandCaptor.getValue();
        assertThat(command.getGroupId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(command.getActorId()).isEqualTo("giver-001");
        assertThat(command.getCommandType()).isEqualTo("GET_RECEIVER_WISHLIST");
    }
}
