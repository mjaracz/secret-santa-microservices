package com.secretsanta.gateway.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record CurrentUserResponse(
        String userId,
        String email,
        String name,
        List<String> roles
) {
}
