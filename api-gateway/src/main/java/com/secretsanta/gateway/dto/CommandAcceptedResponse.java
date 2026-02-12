package com.secretsanta.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommandAcceptedResponse {
    private String commandId;
    private String message;
}
