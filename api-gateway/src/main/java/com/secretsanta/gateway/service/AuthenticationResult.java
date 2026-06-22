package com.secretsanta.gateway.service;

import com.secretsanta.gateway.dto.AuthenticationResponse;
import com.secretsanta.gateway.dto.CommandResponse;

public record AuthenticationResult(
        CommandResponse commandResponse,
        AuthenticationResponse authenticationResponse,
        String refreshToken,
        long refreshTokenExpiresAt
) {

    public boolean isSuccess() {
        return authenticationResponse != null;
    }
}
