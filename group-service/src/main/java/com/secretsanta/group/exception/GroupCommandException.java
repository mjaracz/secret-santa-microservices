package com.secretsanta.group.exception;

import java.io.Serial;
import java.util.Objects;

public class GroupCommandException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public GroupCommandException(String errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
