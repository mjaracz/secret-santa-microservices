package com.secretsanta.wishlist.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretsanta.wishlist.dto.AssignmentResponse;
import com.secretsanta.wishlist.dto.CreateWishlistItemRequest;
import com.secretsanta.wishlist.dto.ReceiverWishlistResponse;
import com.secretsanta.wishlist.dto.UpdateWishlistItemRequest;
import com.secretsanta.wishlist.dto.WishlistItemResponse;
import com.secretsanta.wishlist.dto.WishlistResponse;
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
    public WishlistItemResponse addItem(
            UUID groupId,
            String actorUserId,
            CreateWishlistItemRequest request
    ) {
        requireMembership(groupId, actorUserId);
        requireTitle(request.title());

        WishlistItem item = WishlistItem.builder()
                .groupId(groupId)
                .ownerUserId(actorUserId)
                .title(request.title().trim())
                .description(normalizeOptional(request.description()))
                .url(normalizeOptional(request.url()))
                .build();

        return toItemResponse(wishlistItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public WishlistResponse getOwnWishlist(UUID groupId, String actorUserId) {
        requireMembership(groupId, actorUserId);
        return new WishlistResponse(
                groupId.toString(),
                actorUserId,
                wishlistItemRepository
                        .findByGroupIdAndOwnerUserIdOrderByCreatedAtAsc(groupId, actorUserId)
                        .stream()
                        .map(WishlistService::toItemResponse)
                        .toList()
        );
    }

    @Transactional
    public WishlistItemResponse updateItem(
            UUID groupId,
            String actorUserId,
            UUID itemId,
            UpdateWishlistItemRequest request
    ) {
        requireMembership(groupId, actorUserId);
        requireTitle(request.title());

        WishlistItem item = wishlistItemRepository
                .findByIdAndGroupIdAndOwnerUserId(itemId, groupId, actorUserId)
                .orElseThrow(() -> WishlistException.notFound(
                        "WISHLIST_ITEM_NOT_FOUND",
                        "Wishlist item not found"
                ));

        item.setTitle(request.title().trim());
        item.setDescription(normalizeOptional(request.description()));
        item.setUrl(normalizeOptional(request.url()));

        return toItemResponse(wishlistItemRepository.save(item));
    }

    @Transactional
    public void deleteItem(UUID groupId, String actorUserId, UUID itemId) {
        requireMembership(groupId, actorUserId);

        WishlistItem item = wishlistItemRepository
                .findByIdAndGroupIdAndOwnerUserId(itemId, groupId, actorUserId)
                .orElseThrow(() -> WishlistException.notFound(
                        "WISHLIST_ITEM_NOT_FOUND",
                        "Wishlist item not found"
                ));

        wishlistItemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getMyAssignment(UUID groupId, String actorUserId) {
        DrawAssignmentProjection assignment = requireAssignmentAfterDraw(groupId, actorUserId);
        return toAssignmentResponse(assignment);
    }

    @Transactional(readOnly = true)
    public ReceiverWishlistResponse getReceiverWishlist(UUID groupId, String actorUserId) {
        DrawAssignmentProjection assignment = requireAssignmentAfterDraw(groupId, actorUserId);
        List<WishlistItemResponse> items = wishlistItemRepository
                .findByGroupIdAndOwnerUserIdOrderByCreatedAtAsc(groupId, assignment.getReceiverId())
                .stream()
                .map(WishlistService::toItemResponse)
                .toList();

        return new ReceiverWishlistResponse(
                groupId.toString(),
                assignment.getReceiverId(),
                assignment.getReceiverName(),
                items
        );
    }

    @Transactional
    public AssignmentResponse setGiftPurchased(
            UUID groupId,
            String actorUserId,
            boolean giftPurchased
    ) {
        DrawAssignmentProjection assignment = requireAssignmentAfterDraw(groupId, actorUserId);
        assignment.setGiftPurchased(giftPurchased);
        assignment.setPurchasedAt(giftPurchased ? Instant.now() : null);
        return toAssignmentResponse(assignmentRepository.save(assignment));
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

    private static WishlistItemResponse toItemResponse(WishlistItem item) {
        return new WishlistItemResponse(
                item.getId(),
                item.getGroupId().toString(),
                item.getOwnerUserId(),
                item.getTitle(),
                item.getDescription(),
                item.getUrl(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private static AssignmentResponse toAssignmentResponse(DrawAssignmentProjection assignment) {
        return new AssignmentResponse(
                assignment.getGroupId().toString(),
                assignment.getGiverId(),
                assignment.getReceiverId(),
                assignment.getReceiverName(),
                assignment.isGiftPurchased(),
                assignment.getPurchasedAt()
        );
    }
}
