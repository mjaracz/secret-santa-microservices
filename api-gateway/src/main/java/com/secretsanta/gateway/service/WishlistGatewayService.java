package com.secretsanta.gateway.service;

import com.secretsanta.common.wishlist.commands.AddWishlistItemCommand;
import com.secretsanta.common.wishlist.commands.DeleteWishlistItemCommand;
import com.secretsanta.common.wishlist.commands.GetMyAssignmentCommand;
import com.secretsanta.common.wishlist.commands.GetReceiverWishlistCommand;
import com.secretsanta.common.wishlist.commands.GetWishlistCommand;
import com.secretsanta.common.wishlist.commands.SetGiftPurchasedCommand;
import com.secretsanta.common.wishlist.commands.UpdateWishlistItemCommand;
import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateWishlistItemRequest;
import com.secretsanta.gateway.dto.UpdateGiftPurchaseRequest;
import com.secretsanta.gateway.dto.UpdateWishlistItemRequest;
import com.secretsanta.gateway.security.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class WishlistGatewayService {

    private final CommandDispatcher dispatcher;

    @Value("${kafka.topics.wishlist-commands}")
    private String wishlistCommandsTopic;

    public Mono<CommandResponse> addItem(
            String groupId,
            CreateWishlistItemRequest request,
            AuthenticatedActor actor
    ) {
        AddWishlistItemCommand command = AddWishlistItemCommand.builder()
                .groupId(groupId)
                .title(request.title())
                .description(request.description())
                .url(request.url())
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("ADD_WISHLIST_ITEM");

        return dispatcher.send(wishlistCommandsTopic, command, "ADD_WISHLIST_ITEM");
    }

    public Mono<CommandResponse> getOwnWishlist(
            String groupId,
            AuthenticatedActor actor
    ) {
        GetWishlistCommand command = GetWishlistCommand.builder()
                .groupId(groupId)
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("GET_WISHLIST");

        return dispatcher.send(wishlistCommandsTopic, command, "GET_WISHLIST");
    }

    public Mono<CommandResponse> updateItem(
            String groupId,
            String itemId,
            UpdateWishlistItemRequest request,
            AuthenticatedActor actor
    ) {
        UpdateWishlistItemCommand command = UpdateWishlistItemCommand.builder()
                .groupId(groupId)
                .itemId(itemId)
                .title(request.title())
                .description(request.description())
                .url(request.url())
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("UPDATE_WISHLIST_ITEM");

        return dispatcher.send(wishlistCommandsTopic, command, "UPDATE_WISHLIST_ITEM");
    }

    public Mono<CommandResponse> deleteItem(
            String groupId,
            String itemId,
            AuthenticatedActor actor
    ) {
        DeleteWishlistItemCommand command = DeleteWishlistItemCommand.builder()
                .groupId(groupId)
                .itemId(itemId)
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("DELETE_WISHLIST_ITEM");

        return dispatcher.send(wishlistCommandsTopic, command, "DELETE_WISHLIST_ITEM");
    }

    public Mono<CommandResponse> getMyAssignment(
            String groupId,
            AuthenticatedActor actor
    ) {
        GetMyAssignmentCommand command = GetMyAssignmentCommand.builder()
                .groupId(groupId)
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("GET_MY_ASSIGNMENT");

        return dispatcher.send(wishlistCommandsTopic, command, "GET_MY_ASSIGNMENT");
    }

    public Mono<CommandResponse> setGiftPurchased(
            String groupId,
            UpdateGiftPurchaseRequest request,
            AuthenticatedActor actor
    ) {
        SetGiftPurchasedCommand command = SetGiftPurchasedCommand.builder()
                .groupId(groupId)
                .giftPurchased(request.giftPurchased())
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("SET_GIFT_PURCHASED");

        return dispatcher.send(wishlistCommandsTopic, command, "SET_GIFT_PURCHASED");
    }

    public Mono<CommandResponse> getReceiverWishlist(
            String groupId,
            AuthenticatedActor actor
    ) {
        GetReceiverWishlistCommand command = GetReceiverWishlistCommand.builder()
                .groupId(groupId)
                .actorId(actor.userId())
                .actorRoles(actor.roles())
                .build();
        command.initDefaults("GET_RECEIVER_WISHLIST");

        return dispatcher.send(wishlistCommandsTopic, command, "GET_RECEIVER_WISHLIST");
    }
}
