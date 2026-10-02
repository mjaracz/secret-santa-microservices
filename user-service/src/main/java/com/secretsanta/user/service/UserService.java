package com.secretsanta.user.service;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.user.exception.UserCommandException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.hibernate.exception.ConstraintViolationException;

import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.user.entity.User;
import com.secretsanta.user.security.PasswordDecryptor;
import com.secretsanta.user.repository.UserRepository;

import jakarta.transaction.Transactional;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private static final String EMAIL_ALREADY_EXISTS_CODE =
    "USER_EMAIL_ALREADY_EXISTS";

  private static final String EMAIL_ALREADY_EXISTS_MESSAGE =
    "Email is already registered";

  private static final String EMAIL_NORMALIZED_UNIQUE_INDEX =
    "uk_users_email_normalized";

  private static final String INVALID_CREDENTIALS_CODE =
    "AUTH_INVALID_CREDENTIALS";

  private static final String INVALID_CREDENTIALS_MESSAGE =
    "Invalid email or password";

  private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordDecryptor passwordDecryptor;
  private volatile String timingSafePasswordHash;

  @PostConstruct
  void initializeTimingSafePasswordHash() {
    getTimingSafePasswordHash();
  }

  @Transactional
  public UserAuthenticatedEvent authenticate(AuthenticateUserCommand command) {
    if (command == null
      || command.getEmail() == null
      || command.getEmail().isBlank()
      || command.getEncryptedPassword() == null
      || command.getEncryptedPassword().isBlank()
    ) {
      throw invalidCredentials();
    }

    String password;
    try {
      password = passwordDecryptor.decrypt(command.getEncryptedPassword());
    } catch (IllegalArgumentException exception) {
      throw invalidCredentials();
    }

    if (password.isEmpty()
      || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES
    ) {
      throw invalidCredentials();
    }

    Optional<User> matchingEmail = userRepository.findByEmailNormalized(
      normalizeEmail(command.getEmail())
    );
    String passwordHash = matchingEmail
      .map(User::getPasswordHash)
      .orElseGet(this::getTimingSafePasswordHash);
    boolean passwordMatches = passwordEncoder.matches(password, passwordHash);

    if (matchingEmail.isEmpty()
      || !passwordMatches
      || matchingEmail.get().getStatus() == UserAccountStatus.DELETED
    ) {
      throw invalidCredentials();
    }

    UserAuthenticatedEvent event = UserAuthenticatedEvent.builder()
      .userId(matchingEmail.get().getId().toString())
      .build();
    event.initDefaults("USER_AUTHENTICATED");

    return event;
  }

  @Transactional
  public UserCreatedEvent createUser(CreateUserCommand command) {
    String email = command.getEmail().trim();
    String emailNormalized = normalizeEmail(command.getEmail());

    if (userRepository.existsByEmailNormalized(emailNormalized)) {
      throw emailAlreadyExists();
    }

    String passwordHash =
      passwordEncoder.encode(command.getPassword());

    User user = User.builder()
      .email(email)
      .emailNormalized(emailNormalized)
      .name(command.getName())
      .passwordHash(passwordHash)
      .status(UserAccountStatus.PENDING_VERIFICATION)
      .emailVerifiedAt(null)
      .build();

    User savedUser;

    try {
      savedUser = userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      if (isEmailNormalizedConstraintViolation(exception)) {
        throw emailAlreadyExists();
      }

      throw exception;
    }

    log.info("User created with ID: {}", savedUser.getId());

    return createUserCreatedEvent(savedUser);
  }

  private String normalizeEmail(String email) {
    return email
      .trim()
      .toLowerCase(Locale.ROOT);
  }

  private UserCreatedEvent createUserCreatedEvent(User user) {
    UserCreatedEvent event = UserCreatedEvent.builder()
      .userId(user.getId().toString())
      .email(user.getEmail())
      .name(user.getName())
      .status(user.getStatus())
      .build();

    event.initDefaults("USER_CREATED");

    return event;
  }

  private boolean isEmailNormalizedConstraintViolation(
    Throwable throwable
  ) {
    Throwable current = throwable;

    while (current != null) {
      if (current instanceof ConstraintViolationException violation) {
        String constraintName = violation.getConstraintName();

        boolean isEmailNormalizedUniqueIndex =
          EMAIL_NORMALIZED_UNIQUE_INDEX.equals(constraintName);

        if (isEmailNormalizedUniqueIndex) {
          return true;
        }
      }

      current = current.getCause();
    }

    return false;
  }

  private UserCommandException emailAlreadyExists() {
    return new UserCommandException(
      EMAIL_ALREADY_EXISTS_CODE,
      EMAIL_ALREADY_EXISTS_MESSAGE
    );
  }

  private UserCommandException invalidCredentials() {
    return new UserCommandException(
      INVALID_CREDENTIALS_CODE,
      INVALID_CREDENTIALS_MESSAGE
    );
  }

  private String getTimingSafePasswordHash() {
    if (timingSafePasswordHash == null) {
      synchronized (this) {
        if (timingSafePasswordHash == null) {
          timingSafePasswordHash = passwordEncoder.encode(
            UUID.randomUUID().toString()
          );
        }
      }
    }

    return timingSafePasswordHash;
  }
}
