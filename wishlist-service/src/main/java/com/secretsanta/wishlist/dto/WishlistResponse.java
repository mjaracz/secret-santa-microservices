package com.secretsanta.wishlist.dto;

import java.util.List;

public record WishlistResponse(
        String groupId,
        String ownerUserId,
        List<WishlistItemResponse> items
) {
}
