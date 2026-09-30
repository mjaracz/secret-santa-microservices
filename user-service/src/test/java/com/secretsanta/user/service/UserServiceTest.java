package com.secretsanta.user.service;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.user.entity.User;
import com.secretsanta.user.exception.UserCommandException;
import com.secretsanta.user.repository.UserRepository;
import com.secretsanta.user.security.PasswordDecryptor;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final UUID USER_ID =
		UUID.fromString(
			"11111111-1111-1111-1111-111111111111"
		);

	private static final String EMAIL =
		"New.User@example.com";

	private static final String NORMALIZED_EMAIL =
		"new.user@example.com";

	private static final String PASSWORD =
		"correct-horse-battery-staple";

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordDecryptor passwordDecryptor;

	@Captor
	private ArgumentCaptor<User> userCaptor;

	private PasswordEncoder passwordEncoder;
	private UserService userService;

	@BeforeEach
	void setUp() {
		passwordEncoder = new BCryptPasswordEncoder(4);

		userService = new UserService(
			userRepository,
			passwordEncoder,
			passwordDecryptor
		);
	}

	@Test
	void createsPendingUserWithNormalizedEmailAndHashedPassword() {
		CreateUserCommand command = validCommand();

		when(
			userRepository.existsByEmailNormalized(
				NORMALIZED_EMAIL
			)
		).thenReturn(false);

		when(userRepository.saveAndFlush(any(User.class)))
			.thenAnswer(invocation ->
				withGeneratedId(
					invocation.getArgument(0)
				)
			);

		UserCreatedEvent event =
			userService.createUser(command);

		verify(userRepository)
			.saveAndFlush(userCaptor.capture());

		User persistedUser = userCaptor.getValue();

		assertThat(persistedUser.getEmail())
			.isEqualTo(EMAIL);

		assertThat(persistedUser.getEmailNormalized())
			.isEqualTo(NORMALIZED_EMAIL);

		assertThat(persistedUser.getStatus())
			.isEqualTo(
				UserAccountStatus.PENDING_VERIFICATION
			);

		assertThat(persistedUser.getEmailVerifiedAt())
			.isNull();

		assertThat(persistedUser.getPasswordHash())
			.isNotEqualTo(PASSWORD);

		assertThat(
			passwordEncoder.matches(
				PASSWORD,
				persistedUser.getPasswordHash()
			)
		).isTrue();

		assertThat(event.getUserId())
			.isEqualTo(USER_ID.toString());

		assertThat(event.getEmail())
			.isEqualTo(EMAIL);

		assertThat(event.getName())
			.isEqualTo("New User");

		assertThat(event.getStatus())
			.isEqualTo(
				UserAccountStatus.PENDING_VERIFICATION
			);

		assertThat(event.toString())
			.doesNotContain(PASSWORD);
	}

	@Test
	void authenticatesUserAndReturnsOnlyItsIdentity() {
		AuthenticateUserCommand command = validAuthenticateCommand();
		User user = userWithPassword(PASSWORD, UserAccountStatus.PENDING_VERIFICATION);

		when(passwordDecryptor.decrypt("encrypted-password"))
			.thenReturn(PASSWORD);
		when(userRepository.findByEmailNormalized(NORMALIZED_EMAIL))
			.thenReturn(Optional.of(user));

		UserAuthenticatedEvent event = userService.authenticate(command);

		assertThat(event.getUserId()).isEqualTo(USER_ID.toString());
		assertThat(event.getEventType()).isEqualTo("USER_AUTHENTICATED");
		assertThat(event.getCorrelationId()).isNull();
		verify(passwordDecryptor).decrypt("encrypted-password");
	}

	@Test
	void rejectsIncorrectPasswordWithGenericAuthenticationError() {
		AuthenticateUserCommand command = validAuthenticateCommand();
		User user = userWithPassword("some-other-password", UserAccountStatus.ACTIVE);

		when(passwordDecryptor.decrypt("encrypted-password"))
			.thenReturn(PASSWORD);
		when(userRepository.findByEmailNormalized(NORMALIZED_EMAIL))
			.thenReturn(Optional.of(user));

		assertInvalidCredentials(() -> userService.authenticate(command));
	}

	@Test
	void rejectsUnknownEmailWithGenericAuthenticationError() {
		when(passwordDecryptor.decrypt("encrypted-password"))
			.thenReturn(PASSWORD);
		when(userRepository.findByEmailNormalized(NORMALIZED_EMAIL))
			.thenReturn(Optional.empty());

		assertInvalidCredentials(() ->
			userService.authenticate(validAuthenticateCommand())
		);
	}

	@Test
	void rejectsDeletedAccountEvenWhenPasswordMatches() {
		AuthenticateUserCommand command = validAuthenticateCommand();
		User user = userWithPassword(PASSWORD, UserAccountStatus.DELETED);

		when(passwordDecryptor.decrypt("encrypted-password"))
			.thenReturn(PASSWORD);
		when(userRepository.findByEmailNormalized(NORMALIZED_EMAIL))
			.thenReturn(Optional.of(user));

		assertInvalidCredentials(() -> userService.authenticate(command));
	}

	@Test
	void rejectsUndecryptablePasswordBeforeLookingUpUser() {
		when(passwordDecryptor.decrypt("encrypted-password"))
			.thenThrow(new IllegalArgumentException("invalid ciphertext"));

		assertInvalidCredentials(() ->
			userService.authenticate(validAuthenticateCommand())
		);

		verify(userRepository, never())
			.findByEmailNormalized(any());
	}

	@Test
	void rejectsExistingNormalizedEmail() {
		when(
			userRepository.existsByEmailNormalized(
				NORMALIZED_EMAIL
			)
		).thenReturn(true);

		assertThatThrownBy(() ->
			userService.createUser(validCommand())
		)
			.isInstanceOfSatisfying(
				UserCommandException.class,
				exception -> {
					assertThat(exception.getErrorCode())
						.isEqualTo(
							"USER_EMAIL_ALREADY_EXISTS"
						);

					assertThat(exception.getMessage())
						.isEqualTo(
							"Email is already registered"
						);
				}
			);

		verify(userRepository, never())
			.saveAndFlush(any(User.class));
	}

	@Test
	void mapsEmailUniqueConstraintViolationToDomainError() {
		when(
			userRepository.existsByEmailNormalized(
				NORMALIZED_EMAIL
			)
		).thenReturn(false);

		ConstraintViolationException constraintViolation =
			mock(ConstraintViolationException.class);

		when(constraintViolation.getConstraintName())
			.thenReturn("uk_users_email_normalized");

		DataIntegrityViolationException databaseException =
			new DataIntegrityViolationException(
				"Duplicate email",
				constraintViolation
			);

		when(userRepository.saveAndFlush(any(User.class)))
			.thenThrow(databaseException);

		assertThatThrownBy(() ->
			userService.createUser(validCommand())
		)
			.isInstanceOfSatisfying(
				UserCommandException.class,
				exception -> assertThat(
					exception.getErrorCode()
				).isEqualTo(
					"USER_EMAIL_ALREADY_EXISTS"
				)
			);
	}

	@Test
	void propagatesUnrelatedIntegrityViolation() {
		when(
			userRepository.existsByEmailNormalized(
				NORMALIZED_EMAIL
			)
		).thenReturn(false);

		ConstraintViolationException constraintViolation =
			mock(ConstraintViolationException.class);

		when(constraintViolation.getConstraintName())
			.thenReturn("ck_users_status");

		DataIntegrityViolationException databaseException =
			new DataIntegrityViolationException(
				"Invalid status",
				constraintViolation
			);

		when(userRepository.saveAndFlush(any(User.class)))
			.thenThrow(databaseException);

		assertThatThrownBy(() ->
			userService.createUser(validCommand())
		).isSameAs(databaseException);
	}

	private CreateUserCommand validCommand() {
		return CreateUserCommand.builder()
			.email("  " + EMAIL + "  ")
			.name("New User")
			.password(PASSWORD)
			.build();
	}

	private AuthenticateUserCommand validAuthenticateCommand() {
		return AuthenticateUserCommand.builder()
			.email("  " + EMAIL + "  ")
			.encryptedPassword("encrypted-password")
			.build();
	}

	private User userWithPassword(
		String password,
		UserAccountStatus status
	) {
		return User.builder()
			.id(USER_ID)
			.email(EMAIL)
			.emailNormalized(NORMALIZED_EMAIL)
			.name("New User")
			.passwordHash(passwordEncoder.encode(password))
			.status(status)
			.build();
	}

	private void assertInvalidCredentials(Runnable action) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				UserCommandException.class,
				exception -> {
					assertThat(exception.getErrorCode())
						.isEqualTo("AUTH_INVALID_CREDENTIALS");
					assertThat(exception.getMessage())
						.isEqualTo("Invalid email or password");
				}
			);
	}

	private User withGeneratedId(User user) {
		return User.builder()
			.id(USER_ID)
			.email(user.getEmail())
			.emailNormalized(user.getEmailNormalized())
			.name(user.getName())
			.passwordHash(user.getPasswordHash())
			.status(user.getStatus())
			.emailVerifiedAt(user.getEmailVerifiedAt())
			.version(user.getVersion())
			.build();
	}
}
