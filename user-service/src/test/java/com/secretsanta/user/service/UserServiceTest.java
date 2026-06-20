package com.secretsanta.user.service;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import com.secretsanta.user.entity.User;
import com.secretsanta.user.exception.UserCommandException;
import com.secretsanta.user.repository.UserRepository;
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
	private EmailVerificationService emailVerificationService;

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
			emailVerificationService
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

		EmailVerificationRequestedEvent verificationEvent =
			EmailVerificationRequestedEvent.builder()
				.userId(USER_ID.toString())
				.email(EMAIL)
				.verificationToken("verification-token")
				.build();
		verificationEvent.initDefaults("EMAIL_VERIFICATION_REQUESTED");

		when(emailVerificationService.issueFor(any(User.class)))
			.thenReturn(verificationEvent);

		UserRegistrationResult result =
			userService.createUser(command);
		UserCreatedEvent event = result.userCreatedEvent();

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

		assertThat(result.verificationRequestedEvent())
			.isSameAs(verificationEvent);
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

	private User withGeneratedId(User user) {
		return User.builder()
			.id(USER_ID)
			.email(user.getEmail())
			.emailNormalized(user.getEmailNormalized())
			.name(user.getName())
			.passwordHash(user.getPasswordHash())
			.status(user.getStatus())
			.emailVerifiedAt(user.getEmailVerifiedAt())
			.role(user.getRole())
			.version(user.getVersion())
			.build();
	}
}
