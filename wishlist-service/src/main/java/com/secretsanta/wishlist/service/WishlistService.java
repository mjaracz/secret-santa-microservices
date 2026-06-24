package com.secretsanta.wishlist.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretsanta.common.wishlist.commands.AddWishlistItemCommand;
import com.secretsanta.common.wishlist.commands.DeleteWishlistItemCommand;
import com.secretsanta.common.wishlist.commands.GetMyAssignmentCommand;
import com.secretsanta.common.wishlist.commands.GetReceiverWishlistCommand;
import com.secretsanta.common.wishlist.commands.GetWishlistCommand;
import com.secretsanta.common.wishlist.commands.SetGiftPurchasedCommand;
import com.secretsanta.common.wishlist.commands.UpdateWishlistItemCommand;
import com.secretsanta.common.wishlist.dto.AssignmentDto;
import com.secretsanta.common.wishlist.dto.WishlistItemDto;
import com.secretsanta.common.wishlist.events.GiftPurchaseUpdatedEvent;
import com.secretsanta.common.wishlist.events.ReceiverWishlistFetchedEvent;
import com.secretsanta.common.wishlist.events.WishlistAssignmentFetchedEvent;
import com.secretsanta.common.wishlist.events.WishlistFetchedEvent;
import com.secretsanta.common.wishlist.events.WishlistItemAddedEvent;
import com.secretsanta.common.wishlist.events.WishlistItemDeletedEvent;
import com.secretsanta.common.wishlist.events.WishlistItemUpdatedEvent;
import com.secretsanta.wishlist.entity.DrawAssignmentProjection;
import com.secretsanta.wishlist.entity.GroupProjection;
import com.secretsanta.wishlist.entity.WishlistItem;
import com.secretsanta.wishlist.exception.WishlistException;
import com.secretsanta.wishlist.repository.DrawAssignmentProjectionRepository;
import com.secretsanta.wishlist.repository.GroupParticipantProjectionRepository;
import com.secretsanta.wishlist.repository.GroupProjectionRepository;
import com.secretsanta.wishlist.repository.WishlistItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final GroupProjectionRepository groupRepository;
    private final GroupParticipantProjectionRepository participantRepository;
    private final DrawAssignmentProjectionRepository assignmentRepository;

    @Transactional
    public WishlistItemAddedEvent addItem(AddWishlistItemCommand command) {
        UUID groupId = groupIdOf(command.getGroupId());
        String actorUserId = requireActor(command.getActorId());
        requireMembership(groupId, actorUserId);
        requireTitle(command.getTitle());

        WishlistItem item = WishlistItem.builder()
                .groupId(groupId)
                .ownerUserId(actorUserId)
                .title(command.getTitle().trim())
                .description(normalizeOptional(command.getDescription()))
                .url(normalizeOptional(command.getUrl()))
                .build();

        WishlistItemAddedEvent event = WishlistItemAddedEvent.builder()
                .item(toItemDto(wishlistItemRepository.save(item)))
                .build();
        event.initDefaults("WISHLIST_ITEM_ADDED");
        return event;
    }

    @Transactional(readOnly = true)
    public WishlistFetchedEvent getOwnWishlist(GetWishlistCommand command) {
        UUID groupId = groupIdOf(command.getGroupId());
        String actorUserId = requireActor(command.getActorId());
        requireMembership(groupId, actorUserId);

        WishlistFetchedEvent event = WishlistFetchedEvent.builder()
                .groupId(groupId.toString())
                .ownerUserId(actorUserId)
                .items(wishlistItems(groupId, actorUserId))
                .build();
        event.initDefaults("WISHLIST_FETCHED");
        return event;
    }

    @Transactional
    public WishlistItemUpdatedEvent updateItem(UpdateWishlistItemCommand command) {
        UUID groupId = groupIdOf(command.getGroupId());
        UUID itemId = itemIdOf(command.getItemId());
        String actorUserId = requireActor(command.getActorId());
        requireMembership(groupId, actorUserId);
        requireTitle(command.getTitle());

        WishlistItem item = wishlistItemRepository
                .findByIdAndGroupIdAndOwnerUserId(itemId, groupId, actorUserId)
                .orElseThrow(() -> WishlistException.notFound(
                        "WISHLIST_ITEM_NOT_FOUND",
                        "Wishlist item not found"
                ));

        item.setTitle(command.getTitle().trim());
        item.setDescription(normalizeOptional(command.getDescription()));
        item.setUrl(normalizeOptional(command.getUrl()));

        WishlistItemUpdatedEvent event = WishlistItemUpdatedEvent.builder()
                .item(toItemDto(wishlistItemRepository.save(item)))
                .build();
        event.initDefaults("WISHLIST_ITEM_UPDATED");
        return event;
    }

    @Transactional
    public WishlistItemDeletedEvent deleteItem(DeleteWishlistItemCommand command) {
        UUID groupId = groupIdOf(command.getGroupId());
        UUID itemId = itemIdOf(command.getItemId());
        String actorUserId = requireActor(command.getActorId());
        requireMembership(groupId, actorUserId);

        WishlistItem item = wishlistItemRepository
                .findByIdAndGroupIdAndOwnerUserId(itemId, groupId, actorUserId)
                .orElseThrow(() -> WishlistException.notFound(
                        "WISHLIST_ITEM_NOT_FOUND",
                        "Wishlist item not found"
                ));

        wishlistItemRepository.delete(item);

        WishlistItemDeletedEvent event = WishlistItemDeletedEvent.builder()
                .groupId(groupId.toString())
                .ownerUserId(actorUserId)
                .itemId(itemId.toString())
                .build();
        event.initDefaults("WISHLIST_ITEM_DELETED");
        return event;
    }

    @Transactional(readOnly = true)
    public WishlistAssignmentFetchedEvent getMyAssignment(GetMyAssignmentCommand command) {
        DrawAssignmentProjection assignment = requireAssignmentAfterDraw(
                groupIdOf(command.getGroupId()),
                requireActor(command.getActorId())
        );

        WishlistAssignmentFetchedEvent event = WishlistAssignmentFetchedEvent.builder()
                .assignment(toAssignmentDto(assignment))
                .build();
        event.initDefaults("WISHLIST_ASSIGNMENT_FETCHED");
        return event;
    }

    @Transactional(readOnly = true)
    public ReceiverWishlistFetchedEvent getReceiverWishlist(GetReceiverWishlistCommand command) {
        DrawAssignmentProjection assignment = requireAssignmentAfterDraw(
                groupIdOf(command.getGroupId()),
                requireActor(command.getActorId())
        );
        UUID groupId = assignment.getGroupId();

        ReceiverWishlistFetchedEvent event = ReceiverWishlistFetchedEvent.builder()
                .groupId(groupId.toString())
                .receiverId(assignment.getReceiverId())
                .receiverName(assignment.getReceiverName())
                .items(wishlistItems(groupId, assignment.getReceiverId()))
                .build();
        event.initDefaults("RECEIVER_WISHLIST_FETCHED");
        return event;
    }

    @Transactional
    public GiftPurchaseUpdatedEvent setGiftPurchased(SetGiftPurchasedCommand command) {
        DrawAssignmentProjection assignment = requireAssignmentAfterDraw(
                groupIdOf(command.getGroupId()),
                requireActor(command.getActorId())
        );
        assignment.setGiftPurchased(command.isGiftPurchased());
        assignment.setPurchasedAt(command.isGiftPurchased() ? Instant.now() : null);

        GiftPurchaseUpdatedEvent event = GiftPurchaseUpdatedEvent.builder()
                .assignment(toAssignmentDto(assignmentRepository.save(assignment)))
                .build();
        event.initDefaults("GIFT_PURCHASE_UPDATED");
        return event;
    }

    private DrawAssignmentProjection requireAssignmentAfterDraw(UUID groupId, String actorUserId) {
        requireMembership(groupId, actorUserId);
        requireDrawnGroup(groupId);
        return assignmentRepository
                .findByGroupIdAndGiverId(groupId, actorUserId)
                .orElseThrow(() -> WishlistException.notFound(
                        "WISHLIST_ASSIGNMENT_NOT_FOUND",
                        "Assignment not found for current user"
                ));
    }

    private void requireDrawnGroup(UUID groupId) {
        GroupProjection group = groupRepository.findById(groupId)
                .orElseThrow(() -> WishlistException.notFound(
                        "WISHLIST_GROUP_NOT_FOUND",
                        "Group not found"
                ));

        if (!group.isDrawn()) {
            throw WishlistException.conflict(
                    "WISHLIST_DRAW_NOT_COMPLETED",
                    "Receiver wishlist is available only after draw"
            );
        }
    }

    private void requireMembership(UUID groupId, String userId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> WishlistException.notFound(
                        "WISHLIST_GROUP_NOT_FOUND",
                        "Group not found"
                ));

        if (!participantRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw WishlistException.forbidden("User is not a member of this group");
        }
    }

    private List<WishlistItemDto> wishlistItems(UUID groupId, String ownerUserId) {
        return wishlistItemRepository
                .findByGroupIdAndOwnerUserIdOrderByCreatedAtAsc(groupId, ownerUserId)
                .stream()
                .map(WishlistService::toItemDto)
                .toList();
    }

    private static String requireActor(String actorUserId) {
        if (actorUserId == null || actorUserId.isBlank()) {
            throw WishlistException.unauthorized("Current user is required");
        }
        return actorUserId.trim();
    }

    private static UUID groupIdOf(String groupId) {
        return uuidOf(groupId, "Group ID must be a valid UUID");
    }

    private static UUID itemIdOf(String itemId) {
        return uuidOf(itemId, "Wishlist item ID must be a valid UUID");
    }

    private static UUID uuidOf(String value, String message) {
        if (value == null || value.isBlank()) {
            throw WishlistException.badRequest(message);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw WishlistException.badRequest(message);
        }
    }

    private static void requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw WishlistException.badRequest("Wishlist item title is required");
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static WishlistItemDto toItemDto(WishlistItem item) {
        return WishlistItemDto.builder()
                .id(toStringOrNull(item.getId()))
                .groupId(toStringOrNull(item.getGroupId()))
                .ownerUserId(item.getOwnerUserId())
                .title(item.getTitle())
                .description(item.getDescription())
                .url(item.getUrl())
                .createdAt(toStringOrNull(item.getCreatedAt()))
                .updatedAt(toStringOrNull(item.getUpdatedAt()))
                .build();
    }

    private static AssignmentDto toAssignmentDto(DrawAssignmentProjection assignment) {
        return AssignmentDto.builder()
                .groupId(toStringOrNull(assignment.getGroupId()))
                .giverId(assignment.getGiverId())
                .receiverId(assignment.getReceiverId())
                .receiverName(assignment.getReceiverName())
                .giftPurchased(assignment.isGiftPurchased())
                .purchasedAt(toStringOrNull(assignment.getPurchasedAt()))
                .build();
    }

    private static String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }
}
