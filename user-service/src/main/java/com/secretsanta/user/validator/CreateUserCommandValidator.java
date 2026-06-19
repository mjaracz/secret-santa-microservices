package com.secretsanta.user.validator;

import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.user.exception.UserCommandException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class CreateUserCommandValidator {

	private static final String VALIDATION_ERROR_CODE = "USER_VALIDATION_FAILED";

	private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

	private final Validator validator;

	public void validate(CreateUserCommand command) {
		if (command == null) {
			throw new UserCommandException(
				VALIDATION_ERROR_CODE,
				"Create user command must not be null"
			);
		}

		Set<ConstraintViolation<CreateUserCommand>> violations = validator.validate(command);

		if (!violations.isEmpty()) {
			throw new UserCommandException(
				VALIDATION_ERROR_CODE,
				createViolationMessage(violations)
			);
		}

		validatePasswordByteLength(command.getPassword());
	}

	private void validatePasswordByteLength(String password) {
		if (password == null) {
			return;
		}

		int passwordByteLength = password
			.getBytes(StandardCharsets.UTF_8)
			.length;

		if (passwordByteLength > BCRYPT_MAX_PASSWORD_BYTES) {
			throw new UserCommandException(
				VALIDATION_ERROR_CODE,
				"Password must not exceed 72 bytes in UTF-8 encoding"
			);
		}
	}

	private String createViolationMessage(
		Set<ConstraintViolation<CreateUserCommand>> violations
	) {
		return violations.stream()
			.sorted(
				Comparator
					.comparing(
						(ConstraintViolation<CreateUserCommand> violation) -> violation.getPropertyPath().toString()
					)
					.thenComparing(
						ConstraintViolation::getMessage
					)
			)
			.map(violation ->
				violation.getPropertyPath()
					+ ": "
					+ violation.getMessage()
			)
			.collect(Collectors.joining("; "));
	}
}
