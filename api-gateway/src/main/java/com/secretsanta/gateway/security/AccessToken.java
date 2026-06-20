package com.secretsanta.gateway.security;

public record AccessToken(
        String value,
        long expiresInSeconds
) {
}
