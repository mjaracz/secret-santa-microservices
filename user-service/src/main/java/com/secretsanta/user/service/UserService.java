package com.secretsanta.user.service;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.user.exception.UserCommandException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.hibernate.exception.ConstraintViolationException;

import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.user.entity.User;
import com.secretsanta.user.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

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

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailVerificationService emailVerificationService;

  @Transactional
  public UserRegistrationResult createUser(CreateUserCommand command) {
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
      .role(UserRole.USER)
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

    return new UserRegistrationResult(
      createUserCreatedEvent(savedUser),
      emailVerificationService.issueFor(savedUser)
    );
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
}
