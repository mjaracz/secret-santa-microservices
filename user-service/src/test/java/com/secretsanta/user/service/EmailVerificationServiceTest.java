package com.secretsanta.user.service;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.common.user.commands.ResendEmailVerificationCommand;
import com.secretsanta.common.user.commands.VerifyEmailCommand;
import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import com.secretsanta.user.entity.EmailVerificationToken;
import com.secretsanta.user.entity.User;
import com.secretsanta.user.exception.UserCommandException;
import com.secretsanta.user.repository.EmailVerificationTokenRepository;
import com.secretsanta.user.repository.UserRepository;
import com.secretsanta.user.security.SecureTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private SecureTokenService secureTokenService;

    private EmailVerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new EmailVerificationService(
                userRepository,
                tokenRepository,
                secureTokenService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(24)
        );
    }

    @Test
    void storesOnlyHashAndReturnsRawTokenForEmailDelivery() {
        User user = pendingUser();
        when(secureTokenService.generateToken()).thenReturn("raw-token");
        when(secureTokenService.hash("raw-token"))
                .thenReturn("a".repeat(64));

        EmailVerificationRequestedEvent event =
                verificationService.issueFor(user);

        verify(tokenRepository).save(any(EmailVerificationToken.class));
        assertThat(event.getVerificationToken()).isEqualTo("raw-token");
        assertThat(event.getExpiresAt())
                .isEqualTo(NOW.plus(Duration.ofHours(24)).toEpochMilli());
    }

    @Test
    void activatesUserForValidToken() {
        User user = pendingUser();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash("a".repeat(64))
                .createdAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plusSeconds(60))
                .build();
        when(tokenRepository.findByTokenHashForUpdate("a".repeat(64)))
                .thenReturn(Optional.of(token));

        verificationService.verify(
                VerifyEmailCommand.builder()
                        .tokenHash("a".repeat(64))
                        .build()
        );

        assertThat(user.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        verify(userRepository).save(user);
    }

    @Test
    void rejectsExpiredVerificationToken() {
        User user = pendingUser();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash("a".repeat(64))
                .createdAt(NOW.minus(Duration.ofDays(2)))
                .expiresAt(NOW.minusSeconds(1))
                .build();
        when(tokenRepository.findByTokenHashForUpdate("a".repeat(64)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> verificationService.verify(
                VerifyEmailCommand.builder()
                        .tokenHash("a".repeat(64))
                        .build()
        )).isInstanceOfSatisfying(
                UserCommandException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo("USER_VERIFICATION_TOKEN_INVALID")
        );
    }

    @Test
    void resendDoesNotRevealWhetherAccountExists() {
        when(userRepository.findByEmailNormalized("missing@example.com"))
                .thenReturn(Optional.empty());

        EmailVerificationResendResult result = verificationService.resend(
                ResendEmailVerificationCommand.builder()
                        .email("missing@example.com")
                        .build()
        );

        assertThat(result.responseEvent().isAccepted()).isTrue();
        assertThat(result.notificationEvent()).isNull();
    }

    private User pendingUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("User@example.com")
                .emailNormalized("user@example.com")
                .name("User")
                .passwordHash("bcrypt-hash")
                .status(UserAccountStatus.PENDING_VERIFICATION)
                .role(UserRole.USER)
                .build();
    }
}
