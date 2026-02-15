package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.CommandResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class ResponseMapper {

    private ResponseMapper() {
    }

    static ResponseEntity<CommandResponse> toResponseEntity(CommandResponse response) {
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        if ("Request timed out".equals(response.getError())) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
        }
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }
}
