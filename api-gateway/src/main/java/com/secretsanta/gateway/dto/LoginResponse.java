package com.secretsanta.gateway.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String userId
) {
}
