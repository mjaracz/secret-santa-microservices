package com.secretsanta.wishlist.dto;

public record WishlistErrorResponse(
        String errorCode,
        String message
) {
}
