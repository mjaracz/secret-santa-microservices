package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.CommandResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static com.secretsanta.gateway.dto.CommandResponse.REQUEST_TIMEOUT_ERROR_CODE;

final class ResponseMapper {

    private static final String VALIDATION_ERROR_CODE = "USER_VALIDATION_FAILED";
    private static final String EMAIL_EXISTS_ERROR_CODE = "USER_EMAIL_ALREADY_EXISTS";
    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    private static final String INVALID_CREDENTIALS_ERROR_CODE = "AUTH_INVALID_CREDENTIALS";
    private static final String EMAIL_NOT_VERIFIED_ERROR_CODE = "AUTH_EMAIL_NOT_VERIFIED";
    private static final String INVALID_REFRESH_ERROR_CODE = "AUTH_REFRESH_TOKEN_INVALID";
    private static final String REFRESH_REUSED_ERROR_CODE = "AUTH_REFRESH_TOKEN_REUSED";
    private static final String FORBIDDEN_ERROR_CODE = "AUTH_FORBIDDEN";
    private static final String INVALID_VERIFICATION_ERROR_CODE = "USER_VERIFICATION_TOKEN_INVALID";
    private static final String WISHLIST_VALIDATION_FAILED = "WISHLIST_VALIDATION_FAILED";
    private static final String WISHLIST_UNAUTHORIZED = "WISHLIST_UNAUTHORIZED";
    private static final String WISHLIST_FORBIDDEN = "WISHLIST_FORBIDDEN";
    private static final String WISHLIST_GROUP_NOT_FOUND = "WISHLIST_GROUP_NOT_FOUND";
    private static final String WISHLIST_ITEM_NOT_FOUND = "WISHLIST_ITEM_NOT_FOUND";
    private static final String WISHLIST_ASSIGNMENT_NOT_FOUND = "WISHLIST_ASSIGNMENT_NOT_FOUND";
    private static final String WISHLIST_DRAW_NOT_COMPLETED = "WISHLIST_DRAW_NOT_COMPLETED";

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
            case VALIDATION_ERROR_CODE,
                 WISHLIST_VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case EMAIL_EXISTS_ERROR_CODE,
                 WISHLIST_DRAW_NOT_COMPLETED -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR_CODE -> HttpStatus.INTERNAL_SERVER_ERROR;
            case REQUEST_TIMEOUT_ERROR_CODE -> HttpStatus.GATEWAY_TIMEOUT;
            case INVALID_CREDENTIALS_ERROR_CODE,
                 INVALID_REFRESH_ERROR_CODE,
                 REFRESH_REUSED_ERROR_CODE,
                 WISHLIST_UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case EMAIL_NOT_VERIFIED_ERROR_CODE,
                 FORBIDDEN_ERROR_CODE,
                 WISHLIST_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case INVALID_VERIFICATION_ERROR_CODE -> HttpStatus.BAD_REQUEST;
            case WISHLIST_GROUP_NOT_FOUND,
                 WISHLIST_ITEM_NOT_FOUND,
                 WISHLIST_ASSIGNMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }
}
