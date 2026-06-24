package com.secretsanta.common.wishlist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentDto {

    private String groupId;
    private String giverId;
    private String receiverId;
    private String receiverName;
    private boolean giftPurchased;
    private String purchasedAt;
}
