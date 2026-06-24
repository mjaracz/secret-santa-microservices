package com.secretsanta.common.wishlist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemDto {

    private String id;
    private String groupId;
    private String ownerUserId;
    private String title;
    private String description;
    private String url;
    private String createdAt;
    private String updatedAt;
}
