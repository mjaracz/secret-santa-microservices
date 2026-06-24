package com.secretsanta.wishlist.dto;

import java.time.Instant;
import java.util.UUID;

public record WishlistItemResponse(
        UUID id,
        String groupId,
        String ownerUserId,
        String title,
        String description,
        String url,
        Instant createdAt,
        Instant updatedAt
) {
}
