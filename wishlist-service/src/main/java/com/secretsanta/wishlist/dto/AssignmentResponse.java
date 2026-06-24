package com.secretsanta.wishlist.dto;

import java.time.Instant;

public record AssignmentResponse(
        String groupId,
        String giverId,
        String receiverId,
        String receiverName,
        boolean giftPurchased,
        Instant purchasedAt
) {
}
