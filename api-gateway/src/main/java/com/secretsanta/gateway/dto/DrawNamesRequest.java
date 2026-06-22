package com.secretsanta.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrawNamesRequest {
    @NotBlank(message = "Requested by (owner ID) is required")
    private String requestedBy;
}
