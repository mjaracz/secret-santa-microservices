package com.secretsanta.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {
    private String userId;
    private String userEmail;
    private String userName;
    private String role;
}
