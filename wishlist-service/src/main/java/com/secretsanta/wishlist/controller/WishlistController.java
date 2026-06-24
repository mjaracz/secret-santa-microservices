package com.secretsanta.wishlist.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.secretsanta.wishlist.dto.AssignmentResponse;
import com.secretsanta.wishlist.dto.CreateWishlistItemRequest;
import com.secretsanta.wishlist.dto.ReceiverWishlistResponse;
import com.secretsanta.wishlist.dto.UpdateGiftPurchaseRequest;
import com.secretsanta.wishlist.dto.UpdateWishlistItemRequest;
import com.secretsanta.wishlist.dto.WishlistItemResponse;
import com.secretsanta.wishlist.dto.WishlistResponse;
import com.secretsanta.wishlist.exception.WishlistException;
import com.secretsanta.wishlist.service.WishlistService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
@Validated
public class WishlistController {

    private static final String ACTOR_HEADER = "X-User-Id";

    private final WishlistService wishlistService;

    @PostMapping("/wishlist")
    public ResponseEntity<WishlistItemResponse> addItem(
            @PathVariable UUID groupId,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorUserId,
            @Valid @RequestBody CreateWishlistItemRequest request
    ) {
        WishlistItemResponse response = wishlistService.addItem(
                groupId,
                requireActor(actorUserId),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/wishlist")
    public WishlistResponse getOwnWishlist(
            @PathVariable UUID groupId,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorUserId
    ) {
        return wishlistService.getOwnWishlist(groupId, requireActor(actorUserId));
    }

    @PutMapping("/wishlist/{itemId}")
    public WishlistItemResponse updateItem(
            @PathVariable UUID groupId,
            @PathVariable UUID itemId,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorUserId,
            @Valid @RequestBody UpdateWishlistItemRequest request
    ) {
        return wishlistService.updateItem(groupId, requireActor(actorUserId), itemId, request);
    }

    @DeleteMapping("/wishlist/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable UUID groupId,
            @PathVariable UUID itemId,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorUserId
    ) {
        wishlistService.deleteItem(groupId, requireActor(actorUserId), itemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assignments/me")
    public AssignmentResponse getMyAssignment(
            @PathVariable UUID groupId,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorUserId
    ) {
        return wishlistService.getMyAssignment(groupId, requireActor(actorUserId));
    }

    @PatchMapping("/assignments/me/purchased")
    public AssignmentResponse setGiftPurchased(
            @PathVariable UUID groupId,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorUserId,
            @Valid @RequestBody UpdateGiftPurchaseRequest request
    ) {
        return wishlistService.setGiftPurchased(
                groupId,
                requireActor(actorUserId),
                request.giftPurchased()
        );
    }

    @GetMapping("/receiver-wishlist")
    public ReceiverWishlistResponse getReceiverWishlist(
            @PathVariable UUID groupId,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorUserId
    ) {
        return wishlistService.getReceiverWishlist(groupId, requireActor(actorUserId));
    }

    private static String requireActor(String actorUserId) {
        if (actorUserId == null || actorUserId.isBlank()) {
            throw WishlistException.unauthorized("Current user header is required");
        }
        return actorUserId.trim();
    }
}
