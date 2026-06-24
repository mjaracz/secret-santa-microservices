package com.secretsanta.wishlist.dto;

import java.util.List;

public record ReceiverWishlistResponse(
        String groupId,
        String receiverId,
        String receiverName,
        List<WishlistItemResponse> items
) {
}
