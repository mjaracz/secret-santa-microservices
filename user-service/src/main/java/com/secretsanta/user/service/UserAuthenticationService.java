package com.secretsanta.user.service;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.commands.RefreshSessionCommand;
import com.secretsanta.common.user.commands.RevokeSessionCommand;
import com.secretsanta.common.user.dto.AuthenticatedUserDto;
import com.secretsanta.common.user.events.SessionRefreshedEvent;
import com.secretsanta.common.user.events.SessionRevokedEvent;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
import com.secretsanta.user.entity.RefreshSession;
import com.secretsanta.user.entity.User;
import com.secretsanta.user.exception.UserCommandException;
import com.secretsanta.user.repository.RefreshSessionRepository;
import com.secretsanta.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Service
public class UserAuthenticationService {

    private static final String INVALID_CREDENTIALS_CODE = "AUTH_INVALID_CREDENTIALS";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String EMAIL_NOT_VERIFIED_CODE = "AUTH_EMAIL_NOT_VERIFIED";
    private static final String EMAIL_NOT_VERIFIED_MESSAGE = "Email address has not been verified";
    private static final String INVALID_REFRESH_CODE = "AUTH_REFRESH_TOKEN_INVALID";
    private static final String INVALID_REFRESH_MESSAGE = "Refresh token is invalid or expired";
    private static final String REFRESH_REUSED_CODE = "AUTH_REFRESH_TOKEN_REUSED";
    private static final String REFRESH_REUSED_MESSAGE = "Refresh token reuse was detected";

    private final UserRepository userRepository;
    private final RefreshSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration refreshTokenTtl;
    private final Duration refreshFamilyTtl;
    private final String dummyPasswordHash;

    public UserAuthenticationService(
            UserRepository userRepository,
            RefreshSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${security.refresh-token-ttl:PT168H}") Duration refreshTokenTtl,
            @Value("${security.refresh-token-family-ttl:PT720H}") Duration refreshFamilyTtl
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
        this.refreshFamilyTtl = refreshFamilyTtl;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserAuthenticatedEvent authenticate(AuthenticateUserCommand command) {
        if (command.getPassword() == null
                || command.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }

        Optional<User> candidate = userRepository.findByEmailNormalized(
                command.getEmail().trim().toLowerCase(Locale.ROOT)
        );
        String passwordHash = candidate
                .map(User::getPasswordHash)
                .orElse(dummyPasswordHash);

        boolean passwordMatches = passwordEncoder.matches(
                command.getPassword(),
                passwordHash
        );

        if (candidate.isEmpty() || !passwordMatches) {
            throw invalidCredentials();
        }

        User user = candidate.get();
        requireActive(user);

        Instant now = clock.instant();
        Instant familyExpiresAt = now.plus(refreshFamilyTtl);
        Instant tokenExpiresAt = minimum(
                now.plus(refreshTokenTtl),
                familyExpiresAt
        );

        sessionRepository.save(
                RefreshSession.builder()
                        .user(user)
                        .tokenHash(command.getRefreshTokenHash())
                        .familyId(UUID.randomUUID())
                        .createdAt(now)
                        .expiresAt(tokenExpiresAt)
                        .familyExpiresAt(familyExpiresAt)
                        .build()
        );

        UserAuthenticatedEvent event = UserAuthenticatedEvent.builder()
                .user(toDto(user))
                .refreshTokenExpiresAt(tokenExpiresAt.toEpochMilli())
                .build();
        event.initDefaults("USER_AUTHENTICATED");
        return event;
    }

    @Transactional(noRollbackFor = UserCommandException.class)
    public SessionRefreshedEvent refresh(RefreshSessionCommand command) {
        Instant now = clock.instant();
        RefreshSession current = sessionRepository
                .findByTokenHashForUpdate(command.getCurrentTokenHash())
                .orElseThrow(this::invalidRefreshToken);

        if (current.isRevoked()) {
            if (current.wasRotated()) {
                sessionRepository.revokeFamily(current.getFamilyId(), now);
                throw new UserCommandException(
                        REFRESH_REUSED_CODE,
                        REFRESH_REUSED_MESSAGE
                );
            }
            throw invalidRefreshToken();
        }

        if (current.isExpired(now)) {
            sessionRepository.revokeFamily(current.getFamilyId(), now);
            throw invalidRefreshToken();
        }

        User user = current.getUser();
        requireActive(user);

        current.rotate(now, command.getReplacementTokenHash());
        Instant replacementExpiresAt = minimum(
                now.plus(refreshTokenTtl),
                current.getFamilyExpiresAt()
        );

        sessionRepository.save(
                RefreshSession.builder()
                        .user(user)
                        .tokenHash(command.getReplacementTokenHash())
                        .familyId(current.getFamilyId())
                        .createdAt(now)
                        .expiresAt(replacementExpiresAt)
                        .familyExpiresAt(current.getFamilyExpiresAt())
                        .build()
        );

        SessionRefreshedEvent event = SessionRefreshedEvent.builder()
                .user(toDto(user))
                .refreshTokenExpiresAt(replacementExpiresAt.toEpochMilli())
                .build();
        event.initDefaults("SESSION_REFRESHED");
        return event;
    }

    @Transactional
    public SessionRevokedEvent revoke(RevokeSessionCommand command) {
        Instant now = clock.instant();
        sessionRepository.findByTokenHashForUpdate(command.getTokenHash())
                .ifPresent(session ->
                        sessionRepository.revokeFamily(session.getFamilyId(), now)
                );

        SessionRevokedEvent event = SessionRevokedEvent.builder()
                .revoked(true)
                .build();
        event.initDefaults("SESSION_REVOKED");
        return event;
    }

    private void requireActive(User user) {
        if (user.getStatus() == UserAccountStatus.PENDING_VERIFICATION) {
            throw new UserCommandException(
                    EMAIL_NOT_VERIFIED_CODE,
                    EMAIL_NOT_VERIFIED_MESSAGE
            );
        }
        if (user.getStatus() != UserAccountStatus.ACTIVE) {
            throw invalidCredentials();
        }
    }

    private AuthenticatedUserDto toDto(User user) {
        return AuthenticatedUserDto.builder()
                .userId(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }

    private Instant minimum(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private UserCommandException invalidCredentials() {
        return new UserCommandException(
                INVALID_CREDENTIALS_CODE,
                INVALID_CREDENTIALS_MESSAGE
        );
    }

    private UserCommandException invalidRefreshToken() {
        return new UserCommandException(
                INVALID_REFRESH_CODE,
                INVALID_REFRESH_MESSAGE
        );
    }
}
