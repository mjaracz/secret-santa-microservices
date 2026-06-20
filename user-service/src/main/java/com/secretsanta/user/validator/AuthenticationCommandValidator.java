package com.secretsanta.user.validator;

import com.secretsanta.common.BaseCommand;
import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.user.exception.UserCommandException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AuthenticationCommandValidator {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final String VALIDATION_ERROR_CODE = "USER_VALIDATION_FAILED";

    private final Validator validator;

    public AuthenticationCommandValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(BaseCommand command) {
        if (command == null) {
            throw validationFailure("Command must not be null");
        }

        Set<ConstraintViolation<BaseCommand>> violations = validator.validate(command);
        if (!violations.isEmpty()) {
            throw validationFailure(createViolationMessage(violations));
        }

        if (command instanceof AuthenticateUserCommand authenticate
                && authenticate.getPassword() != null
                && authenticate.getPassword().getBytes(StandardCharsets.UTF_8).length
                > BCRYPT_MAX_PASSWORD_BYTES) {
            throw validationFailure(
                    "password: Password must not exceed 72 bytes in UTF-8 encoding"
            );
        }
    }

    private String createViolationMessage(
            Set<ConstraintViolation<BaseCommand>> violations
    ) {
        return violations.stream()
                .sorted(
                        Comparator
                                .comparing(
                                        (ConstraintViolation<BaseCommand> violation) -> violation
                                                .getPropertyPath()
                                                .toString()
                                )
                                .thenComparing(ConstraintViolation::getMessage)
                )
                .map(violation ->
                        violation.getPropertyPath()
                                + ": "
                                + violation.getMessage()
                )
                .collect(Collectors.joining("; "));
    }

    private UserCommandException validationFailure(String reason) {
        return new UserCommandException(VALIDATION_ERROR_CODE, reason);
    }
}
