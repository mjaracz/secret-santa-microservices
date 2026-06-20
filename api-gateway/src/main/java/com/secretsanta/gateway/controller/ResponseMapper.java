package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.CommandResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static com.secretsanta.gateway.dto.CommandResponse.REQUEST_TIMEOUT_ERROR_CODE;

final class ResponseMapper {

    private static final String VALIDATION_ERROR_CODE = "USER_VALIDATION_FAILED";
    private static final String EMAIL_EXISTS_ERROR_CODE = "USER_EMAIL_ALREADY_EXISTS";
    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";

    private ResponseMapper() {
    }

    static ResponseEntity<CommandResponse> toResponseEntity(CommandResponse response) {
        return toResponseEntity(response, HttpStatus.OK);
    }

    static ResponseEntity<CommandResponse> toResponseEntity(
            CommandResponse response,
            HttpStatus successStatus
    ) {
        if (response.isSuccess()) {
            return ResponseEntity.status(successStatus).body(response);
        }

        HttpStatus failureStatus = resolveFailureStatus(response.getErrorCode());
        return ResponseEntity.status(failureStatus).body(response);
    }

    private static HttpStatus resolveFailureStatus(String errorCode) {
        if (errorCode == null) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }

        return switch (errorCode) {
            case VALIDATION_ERROR_CODE -> HttpStatus.BAD_REQUEST;
            case EMAIL_EXISTS_ERROR_CODE -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR_CODE -> HttpStatus.INTERNAL_SERVER_ERROR;
            case REQUEST_TIMEOUT_ERROR_CODE -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }
}
