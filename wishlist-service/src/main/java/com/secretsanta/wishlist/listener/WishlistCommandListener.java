package com.secretsanta.wishlist.listener;

import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.CommandFailedEvent;
import com.secretsanta.common.wishlist.commands.AddWishlistItemCommand;
import com.secretsanta.common.wishlist.commands.DeleteWishlistItemCommand;
import com.secretsanta.common.wishlist.commands.GetMyAssignmentCommand;
import com.secretsanta.common.wishlist.commands.GetReceiverWishlistCommand;
import com.secretsanta.common.wishlist.commands.GetWishlistCommand;
import com.secretsanta.common.wishlist.commands.SetGiftPurchasedCommand;
import com.secretsanta.common.wishlist.commands.UpdateWishlistItemCommand;
import com.secretsanta.common.wishlist.events.GiftPurchaseUpdatedEvent;
import com.secretsanta.common.wishlist.events.ReceiverWishlistFetchedEvent;
import com.secretsanta.common.wishlist.events.WishlistAssignmentFetchedEvent;
import com.secretsanta.common.wishlist.events.WishlistFetchedEvent;
import com.secretsanta.common.wishlist.events.WishlistItemAddedEvent;
import com.secretsanta.common.wishlist.events.WishlistItemDeletedEvent;
import com.secretsanta.common.wishlist.events.WishlistItemUpdatedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.wishlist.exception.WishlistException;
import com.secretsanta.wishlist.service.WishlistService;

@Component
public class WishlistCommandListener {

    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal error while processing wishlist command";

    private final KafkaServiceBus serviceBus;
    private final WishlistService wishlistService;

    @Value("${kafka.topics.wishlist-events}")
    private String wishlistEventsTopic;

    public WishlistCommandListener(
            KafkaServiceBus serviceBus,
            WishlistService wishlistService
    ) {
        this.serviceBus = serviceBus;
        this.wishlistService = wishlistService;
        serviceBus.registerCommandHandler(AddWishlistItemCommand.class, this::onAddItem);
        serviceBus.registerCommandHandler(GetWishlistCommand.class, this::onGetWishlist);
        serviceBus.registerCommandHandler(UpdateWishlistItemCommand.class, this::onUpdateItem);
        serviceBus.registerCommandHandler(DeleteWishlistItemCommand.class, this::onDeleteItem);
        serviceBus.registerCommandHandler(GetMyAssignmentCommand.class, this::onGetMyAssignment);
        serviceBus.registerCommandHandler(SetGiftPurchasedCommand.class, this::onSetGiftPurchased);
        serviceBus.registerCommandHandler(GetReceiverWishlistCommand.class, this::onGetReceiverWishlist);
    }

    @KafkaListener(
            topics = "${kafka.topics.wishlist-commands}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void listen(String message) {
        serviceBus.handleCommandMessage(message, this::emitInternalFailure);
    }

    private void onAddItem(AddWishlistItemCommand command) {
        handle(
                command,
                () -> wishlistService.addItem(command),
                event -> event.getItem().getId()
        );
    }

    private void onGetWishlist(GetWishlistCommand command) {
        handle(
                command,
                () -> wishlistService.getOwnWishlist(command),
                WishlistFetchedEvent::getGroupId
        );
    }

    private void onUpdateItem(UpdateWishlistItemCommand command) {
        handle(
                command,
                () -> wishlistService.updateItem(command),
                event -> event.getItem().getId()
        );
    }

    private void onDeleteItem(DeleteWishlistItemCommand command) {
        handle(
                command,
                () -> wishlistService.deleteItem(command),
                WishlistItemDeletedEvent::getItemId
        );
    }

    private void onGetMyAssignment(GetMyAssignmentCommand command) {
        handle(
                command,
                () -> wishlistService.getMyAssignment(command),
                event -> event.getAssignment().getGiverId()
        );
    }

    private void onSetGiftPurchased(SetGiftPurchasedCommand command) {
        handle(
                command,
                () -> wishlistService.setGiftPurchased(command),
                event -> event.getAssignment().getGiverId()
        );
    }

    private void onGetReceiverWishlist(GetReceiverWishlistCommand command) {
        handle(
                command,
                () -> wishlistService.getReceiverWishlist(command),
                ReceiverWishlistFetchedEvent::getReceiverId
        );
    }

    private <T extends BaseEvent> void handle(
            BaseCommand command,
            Supplier<T> handler,
            Function<T, String> keyExtractor
    ) {
        try {
            T event = handler.get();
            event.setCorrelationId(command.getCommandId());
            serviceBus.emitEvent(wishlistEventsTopic, keyExtractor.apply(event), event);
        } catch (WishlistException exception) {
            emitFailure(command, exception.getErrorCode(), exception.getMessage());
        }
    }

    private void emitInternalFailure(BaseCommand command, String reason) {
        emitFailure(command, INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE);
    }

    private void emitFailure(
            BaseCommand command,
            String errorCode,
            String reason
    ) {
        CommandFailedEvent failedEvent = CommandFailedEvent.builder()
                .correlationId(command.getCommandId())
                .errorCode(errorCode)
                .reason(reason)
                .originalCommandType(command.getCommandType())
                .build();
        failedEvent.initDefaults("COMMAND_FAILED");
        serviceBus.emitEvent(wishlistEventsTopic, command.getCommandId(), failedEvent);
    }
}
