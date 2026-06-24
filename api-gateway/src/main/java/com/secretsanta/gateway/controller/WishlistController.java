package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.CommandResponse;
import com.secretsanta.gateway.dto.CreateWishlistItemRequest;
import com.secretsanta.gateway.dto.UpdateGiftPurchaseRequest;
import com.secretsanta.gateway.dto.UpdateWishlistItemRequest;
import com.secretsanta.gateway.security.AuthenticatedActor;
import com.secretsanta.gateway.service.WishlistGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
@Validated
public class WishlistController {

    private final WishlistGatewayService wishlistGatewayService;

    @PostMapping("/wishlist")
    public Mono<ResponseEntity<CommandResponse>> addItem(
            @PathVariable String groupId,
            @Valid @RequestBody CreateWishlistItemRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return wishlistGatewayService
                .addItem(groupId, request, AuthenticatedActor.from(jwt))
                .map(response -> ResponseMapper.toResponseEntity(response, HttpStatus.CREATED));
    }

    @GetMapping("/wishlist")
    public Mono<ResponseEntity<CommandResponse>> getOwnWishlist(
            @PathVariable String groupId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return wishlistGatewayService
                .getOwnWishlist(groupId, AuthenticatedActor.from(jwt))
                .map(ResponseMapper::toResponseEntity);
    }

    @PutMapping("/wishlist/{itemId}")
    public Mono<ResponseEntity<CommandResponse>> updateItem(
            @PathVariable String groupId,
            @PathVariable String itemId,
            @Valid @RequestBody UpdateWishlistItemRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return wishlistGatewayService
                .updateItem(groupId, itemId, request, AuthenticatedActor.from(jwt))
                .map(ResponseMapper::toResponseEntity);
    }

    @DeleteMapping("/wishlist/{itemId}")
    public Mono<ResponseEntity<CommandResponse>> deleteItem(
            @PathVariable String groupId,
            @PathVariable String itemId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return wishlistGatewayService
                .deleteItem(groupId, itemId, AuthenticatedActor.from(jwt))
                .map(ResponseMapper::toResponseEntity);
    }

    @GetMapping("/assignments/me")
    public Mono<ResponseEntity<CommandResponse>> getMyAssignment(
            @PathVariable String groupId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return wishlistGatewayService
                .getMyAssignment(groupId, AuthenticatedActor.from(jwt))
                .map(ResponseMapper::toResponseEntity);
    }

    @PatchMapping("/assignments/me/purchased")
    public Mono<ResponseEntity<CommandResponse>> setGiftPurchased(
            @PathVariable String groupId,
            @Valid @RequestBody UpdateGiftPurchaseRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return wishlistGatewayService
                .setGiftPurchased(groupId, request, AuthenticatedActor.from(jwt))
                .map(ResponseMapper::toResponseEntity);
    }

    @GetMapping("/receiver-wishlist")
    public Mono<ResponseEntity<CommandResponse>> getReceiverWishlist(
            @PathVariable String groupId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return wishlistGatewayService
                .getReceiverWishlist(groupId, AuthenticatedActor.from(jwt))
                .map(ResponseMapper::toResponseEntity);
    }
}
