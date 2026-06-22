package com.secretsanta.user.service;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.commands.RefreshSessionCommand;
import com.secretsanta.common.user.events.SessionRefreshedEvent;
import com.secretsanta.common.user.events.UserAuthenticatedEvent;
import com.secretsanta.user.entity.RefreshSession;
import com.secretsanta.user.entity.User;
import com.secretsanta.user.exception.UserCommandException;
import com.secretsanta.user.repository.RefreshSessionRepository;
import com.secretsanta.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshSessionRepository sessionRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private UserAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        authenticationService = new UserAuthenticationService(
                userRepository,
                sessionRepository,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(7),
                Duration.ofDays(30)
        );
    }

    @Test
    void authenticatesActiveUserWithPasswordEncoderMatches() {
        User user = activeUser();
        when(userRepository.findByEmailNormalized("user@example.com"))
                .thenReturn(Optional.of(user));

        UserAuthenticatedEvent event = authenticationService.authenticate(
                authenticateCommand(PASSWORD)
        );

        ArgumentCaptor<RefreshSession> sessionCaptor =
                ArgumentCaptor.forClass(RefreshSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());

        assertThat(event.getUser().getUserId())
                .isEqualTo(user.getId().toString());
        assertThat(event.getUser().getRole()).isEqualTo(UserRole.USER);
        assertThat(event.getRefreshTokenExpiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)).toEpochMilli());
        assertThat(sessionCaptor.getValue().getTokenHash())
                .isEqualTo("a".repeat(64));
    }

    @Test
    void returnsSameGenericErrorForUnknownEmailAndWrongPassword() {
        when(userRepository.findByEmailNormalized("user@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.authenticate(
                authenticateCommand("wrong-password")
        )).isInstanceOfSatisfying(
                UserCommandException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo("AUTH_INVALID_CREDENTIALS");
                    assertThat(exception.getMessage())
                            .isEqualTo("Invalid email or password");
                }
        );
    }

    @Test
    void rejectsPendingUserUntilEmailIsVerified() {
        User user = user(UserAccountStatus.PENDING_VERIFICATION);
        when(userRepository.findByEmailNormalized("user@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationService.authenticate(
                authenticateCommand(PASSWORD)
        )).isInstanceOfSatisfying(
                UserCommandException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo("AUTH_EMAIL_NOT_VERIFIED")
        );
    }

    @Test
    void rotatesRefreshTokenWithinSameSessionFamily() {
        User user = activeUser();
        UUID familyId = UUID.randomUUID();
        RefreshSession current = RefreshSession.builder()
                .user(user)
                .tokenHash("a".repeat(64))
                .familyId(familyId)
                .createdAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plus(Duration.ofDays(7)))
                .familyExpiresAt(NOW.plus(Duration.ofDays(30)))
                .build();
        when(sessionRepository.findByTokenHashForUpdate("a".repeat(64)))
                .thenReturn(Optional.of(current));

        SessionRefreshedEvent event = authenticationService.refresh(
                RefreshSessionCommand.builder()
                        .currentTokenHash("a".repeat(64))
                        .replacementTokenHash("b".repeat(64))
                        .build()
        );

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedByTokenHash())
                .isEqualTo("b".repeat(64));
        assertThat(event.getUser().getUserId())
                .isEqualTo(user.getId().toString());

        ArgumentCaptor<RefreshSession> replacementCaptor =
                ArgumentCaptor.forClass(RefreshSession.class);
        verify(sessionRepository).save(replacementCaptor.capture());
        assertThat(replacementCaptor.getValue().getFamilyId())
                .isEqualTo(familyId);
    }

    @Test
    void revokesWholeFamilyWhenRotatedTokenIsReused() {
        UUID familyId = UUID.randomUUID();
        RefreshSession reused = RefreshSession.builder()
                .user(activeUser())
                .tokenHash("a".repeat(64))
                .familyId(familyId)
                .createdAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plusSeconds(60))
                .familyExpiresAt(NOW.plusSeconds(120))
                .revokedAt(NOW.minusSeconds(10))
                .replacedByTokenHash("b".repeat(64))
                .build();
        when(sessionRepository.findByTokenHashForUpdate("a".repeat(64)))
                .thenReturn(Optional.of(reused));

        assertThatThrownBy(() -> authenticationService.refresh(
                RefreshSessionCommand.builder()
                        .currentTokenHash("a".repeat(64))
                        .replacementTokenHash("c".repeat(64))
                        .build()
        )).isInstanceOfSatisfying(
                UserCommandException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo("AUTH_REFRESH_TOKEN_REUSED")
        );

        verify(sessionRepository).revokeFamily(familyId, NOW);
    }

    private AuthenticateUserCommand authenticateCommand(String password) {
        return AuthenticateUserCommand.builder()
                .email(" User@example.com ")
                .password(password)
                .refreshTokenHash("a".repeat(64))
                .build();
    }

    private User activeUser() {
        return user(UserAccountStatus.ACTIVE);
    }

    private User user(UserAccountStatus status) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("User@example.com")
                .emailNormalized("user@example.com")
                .name("User")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .status(status)
                .emailVerifiedAt(
                        status == UserAccountStatus.ACTIVE ? NOW : null
                )
                .role(UserRole.USER)
                .build();
    }
}
