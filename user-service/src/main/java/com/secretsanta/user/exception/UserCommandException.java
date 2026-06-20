package com.secretsanta.user.exception;

import java.io.Serial;
import java.util.Objects;

public class UserCommandException extends IllegalArgumentException {
	@Serial
	private static final long serialVersionUID = 1L;

	private final String errorCode;

	public UserCommandException(String errorCode, String message) {
		super(message);
		this.errorCode = Objects.requireNonNull(
			errorCode,
			"errorCode must not be null"
		);
	}

	public String getErrorCode() {
		return errorCode;
	}
}
