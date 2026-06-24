package com.secretsanta.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWishlistItemRequest(
        @NotBlank(message = "Wishlist item title is required")
        @Size(max = 255, message = "Wishlist item title must be at most 255 characters")
        String title,

        @Size(max = 2048, message = "Wishlist item description must be at most 2048 characters")
        String description,

        @Size(max = 2048, message = "Wishlist item URL must be at most 2048 characters")
        String url
) {
}
