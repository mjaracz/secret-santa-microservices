package com.secretsanta.gateway.controller;

import com.secretsanta.gateway.dto.CommandResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseMapperTest {

    @ParameterizedTest
    @CsvSource({
            "USER_VALIDATION_FAILED, 400",
            "USER_EMAIL_ALREADY_EXISTS, 409",
            "INTERNAL_ERROR, 500",
            "REQUEST_TIMEOUT, 504",
            "UNKNOWN_BUSINESS_ERROR, 422"
    })
    void mapsErrorCodeToExpectedHttpStatus(
            String errorCode,
            int expectedStatus
    ) {
        CommandResponse response = CommandResponse.failure(
                "command-123",
                errorCode,
                "Failure",
                "CREATE_USER"
        );

        ResponseEntity<CommandResponse> result =
                ResponseMapper.toResponseEntity(response);

        assertThat(result.getStatusCode().value())
                .isEqualTo(expectedStatus);
    }

    @Test
    void mapsMissingErrorCodeToUnprocessableEntity() {
        CommandResponse response = CommandResponse.failure(
                "command-123",
                null,
                "Business rule violation",
                "CREATE_GROUP"
        );

        ResponseEntity<CommandResponse> result =
                ResponseMapper.toResponseEntity(response);

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void usesProvidedSuccessStatus() {
        CommandResponse response = CommandResponse.success(
                "command-123",
                null
        );

        ResponseEntity<CommandResponse> result =
                ResponseMapper.toResponseEntity(
                        response,
                        HttpStatus.CREATED
                );

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void defaultsSuccessfulResponseToOk() {
        CommandResponse response = CommandResponse.success(
                "command-123",
                null
        );

        ResponseEntity<CommandResponse> result =
                ResponseMapper.toResponseEntity(response);

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
