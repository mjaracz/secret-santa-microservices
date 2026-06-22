package com.secretsanta.gateway.dto;

import com.secretsanta.common.user.dto.AuthenticatedUserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private AuthenticatedUserDto user;
}
