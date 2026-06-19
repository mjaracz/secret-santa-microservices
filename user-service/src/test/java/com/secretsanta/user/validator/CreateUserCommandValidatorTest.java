package com.secretsanta.user.validator;

import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.user.exception.UserCommandException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class CreateUserCommandValidatorTest {

	private static ValidatorFactory validatorFactory;
	private static CreateUserCommandValidator commandValidator;

	@BeforeAll
	static void setUpValidator() {
		validatorFactory =
			Validation.buildDefaultValidatorFactory();

		commandValidator =
			new CreateUserCommandValidator(
				validatorFactory.getValidator()
			);
	}

	@AfterAll
	static void closeValidatorFactory() {
		validatorFactory.close();
	}

	@Test
	void rejectsInvalidEmail() {
		CreateUserCommand command =
			validCommand("invalid-email", "New User", validPassword());

		UserCommandException exception =
			validateAndGetException(command);

		assertThat(exception.getMessage())
			.contains("email");
	}

	@Test
	void rejectsBlankName() {
		CreateUserCommand command =
			validCommand(
				"user@example.com",
				" ",
				validPassword()
			);

		UserCommandException exception =
			validateAndGetException(command);

		assertThat(exception.getMessage())
			.contains("name");
	}

	@Test
	void rejectsPasswordShorterThanTwelveCharacters() {
		CreateUserCommand command =
			validCommand(
				"user@example.com",
				"New User",
				"short"
			);

		UserCommandException exception =
			validateAndGetException(command);

		assertThat(exception.getMessage())
			.contains("password");
	}

	@Test
	void rejectsPasswordLongerThanSeventyTwoUtf8Bytes() {
		String password = "ą".repeat(37);

		CreateUserCommand command =
			validCommand(
				"user@example.com",
				"New User",
				password
			);

		UserCommandException exception =
			validateAndGetException(command);

		assertThat(exception.getMessage())
			.contains("72 bytes");
	}

	@Test
	void acceptsValidCommand() {
		assertThatCode(() ->
			commandValidator.validate(
				validCommand(
					"user@example.com",
					"New User",
					validPassword()
				)
			)
		).doesNotThrowAnyException();
	}

	@Test
	void rejectsNullCommand() {
		UserCommandException exception =
			validateAndGetException(null);

		assertThat(exception.getMessage())
			.isEqualTo("Create user command must not be null");
	}

	private UserCommandException validateAndGetException(
		CreateUserCommand command
	) {
		UserCommandException exception =
			catchThrowableOfType(
				() -> commandValidator.validate(command),
				UserCommandException.class
			);

		assertThat(exception.getErrorCode())
			.isEqualTo("USER_VALIDATION_FAILED");

		return exception;
	}

	private CreateUserCommand validCommand(
		String email,
		String name,
		String password
	) {
		return CreateUserCommand.builder()
			.email(email)
			.name(name)
			.password(password)
			.build();
	}

	private String validPassword() {
		return "correct-horse-battery-staple";
	}
}
