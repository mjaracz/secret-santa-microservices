package com.secretsanta.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.events.UserCreatedEvent;
import com.secretsanta.user.entity.User;
import com.secretsanta.user.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public UserCreatedEvent createUser(CreateUserCommand command) {

    if (userRepository.existsByEmail(command.getEmail())) {
      throw new IllegalArgumentException("Email already registered" + command.getEmail());
    }

    User user = User.builder()
        .email(command.getEmail())
        .name(command.getName())
        .passwordHash(passwordEncoder.encode(command.getPassword()))
        .build();

    User savedUser = userRepository.save(user);
    log.info("User created with ID: {}", savedUser.getId());

    UserCreatedEvent event = UserCreatedEvent.builder()
        .userId(savedUser.getId().toString())
        .email(savedUser.getEmail())
        .name(savedUser.getName())
        .build();
    event.initDefaults("USER_CREATED");

    return event;
  }
}
