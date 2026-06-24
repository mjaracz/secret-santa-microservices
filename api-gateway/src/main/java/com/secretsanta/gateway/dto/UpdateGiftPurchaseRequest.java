package com.secretsanta.gateway.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateGiftPurchaseRequest(
        @NotNull(message = "Gift purchased flag is required")
        Boolean giftPurchased
) {
}
