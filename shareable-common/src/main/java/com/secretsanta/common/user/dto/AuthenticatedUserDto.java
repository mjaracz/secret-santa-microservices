package com.secretsanta.common.user.dto;

import com.secretsanta.common.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUserDto {

    private String userId;
    private String email;
    private String name;
    private UserRole role;
}
