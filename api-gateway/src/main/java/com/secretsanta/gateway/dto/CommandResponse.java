package com.secretsanta.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.secretsanta.common.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommandResponse {

    public static final String REQUEST_TIMEOUT_ERROR_CODE = "REQUEST_TIMEOUT";

    private boolean success;
    private String commandId;
    private BaseEvent data;
    private String errorCode;
    private String error;
    private String originalCommandType;

    public static CommandResponse success(
            String commandId,
            BaseEvent event
    ) {
        return CommandResponse.builder()
                .success(true)
                .commandId(commandId)
                .data(event)
                .build();
    }

    public static CommandResponse failure(
            String commandId,
            String errorCode,
            String error,
            String originalCommandType
    ) {
        return CommandResponse.builder()
                .success(false)
                .commandId(commandId)
                .errorCode(errorCode)
                .error(error)
                .originalCommandType(originalCommandType)
                .build();
    }
}
