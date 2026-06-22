package com.secretsanta.user.validator;

import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.commands.RefreshSessionCommand;
import com.secretsanta.user.exception.UserCommandException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationCommandValidatorTest {

    private static ValidatorFactory validatorFactory;
    private static AuthenticationCommandValidator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = new AuthenticationCommandValidator(
                validatorFactory.getValidator()
        );
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidAuthenticationCommand() {
        assertThatCode(() -> validator.validate(
                AuthenticateUserCommand.builder()
                        .email("user@example.com")
                        .password("correct-horse-battery-staple")
                        .refreshTokenHash("a".repeat(64))
                        .build()
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordLongerThanBcryptByteLimit() {
        assertThatThrownBy(() -> validator.validate(
                AuthenticateUserCommand.builder()
                        .email("user@example.com")
                        .password("😀".repeat(19))
                        .refreshTokenHash("a".repeat(64))
                        .build()
        )).isInstanceOfSatisfying(
                UserCommandException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo("USER_VALIDATION_FAILED");
                    assertThat(exception.getMessage())
                            .contains("72 bytes");
                }
        );
    }

    @Test
    void rejectsMalformedRefreshTokenHash() {
        assertThatThrownBy(() -> validator.validate(
                RefreshSessionCommand.builder()
                        .currentTokenHash("not-a-hash")
                        .replacementTokenHash("b".repeat(64))
                        .build()
        )).isInstanceOf(UserCommandException.class);
    }
}
