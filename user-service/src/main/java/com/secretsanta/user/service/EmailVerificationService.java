package com.secretsanta.user.service;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.commands.ResendEmailVerificationCommand;
import com.secretsanta.common.user.commands.VerifyEmailCommand;
import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import com.secretsanta.common.user.events.EmailVerificationResentEvent;
import com.secretsanta.common.user.events.EmailVerifiedEvent;
import com.secretsanta.user.entity.EmailVerificationToken;
import com.secretsanta.user.entity.User;
import com.secretsanta.user.exception.UserCommandException;
import com.secretsanta.user.repository.EmailVerificationTokenRepository;
import com.secretsanta.user.repository.UserRepository;
import com.secretsanta.user.security.SecureTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class EmailVerificationService {

    private static final String INVALID_TOKEN_CODE = "USER_VERIFICATION_TOKEN_INVALID";
    private static final String INVALID_TOKEN_MESSAGE = "Verification token is invalid or expired";

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final SecureTokenService secureTokenService;
    private final Clock clock;
    private final Duration tokenTtl;

    public EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationTokenRepository tokenRepository,
            SecureTokenService secureTokenService,
            Clock clock,
            @Value("${security.email-verification-token-ttl:PT24H}") Duration tokenTtl
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.secureTokenService = secureTokenService;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
    }

    EmailVerificationRequestedEvent issueFor(User user) {
        Instant now = clock.instant();
        tokenRepository.invalidateActiveForUser(user.getId(), now);

        String rawToken = secureTokenService.generateToken();
        Instant expiresAt = now.plus(tokenTtl);

        tokenRepository.save(
                EmailVerificationToken.builder()
                        .user(user)
                        .tokenHash(secureTokenService.hash(rawToken))
                        .createdAt(now)
                        .expiresAt(expiresAt)
                        .build()
        );

        return verificationRequestedEvent(user, rawToken, expiresAt);
    }

    @Transactional
    public EmailVerifiedEvent verify(VerifyEmailCommand command) {
        Instant now = clock.instant();
        EmailVerificationToken token = tokenRepository
                .findByTokenHashForUpdate(command.getTokenHash())
                .orElseThrow(this::invalidToken);
        User user = token.getUser();

        if (token.getUsedAt() != null && user.getStatus() == UserAccountStatus.ACTIVE) {
            return verifiedEvent(user);
        }

        if (!token.isUsable(now) || user.getStatus() != UserAccountStatus.PENDING_VERIFICATION) {
            throw invalidToken();
        }

        token.markUsed(now);
        user.verifyEmail(now);
        tokenRepository.invalidateActiveForUser(user.getId(), now);
        userRepository.save(user);

        return verifiedEvent(user);
    }

    @Transactional
    public EmailVerificationResendResult resend(
            ResendEmailVerificationCommand command
    ) {
        EmailVerificationResentEvent responseEvent = EmailVerificationResentEvent.builder()
                .accepted(true)
                .build();
        responseEvent.initDefaults("EMAIL_VERIFICATION_RESENT");

        Optional<User> candidate = userRepository.findByEmailNormalized(
                command.getEmail().trim().toLowerCase(Locale.ROOT)
        );

        if (candidate.isEmpty()
                || candidate.get().getStatus() != UserAccountStatus.PENDING_VERIFICATION) {
            return new EmailVerificationResendResult(responseEvent, null);
        }

        return new EmailVerificationResendResult(
                responseEvent,
                issueFor(candidate.get())
        );
    }

    private EmailVerificationRequestedEvent verificationRequestedEvent(
            User user,
            String rawToken,
            Instant expiresAt
    ) {
        EmailVerificationRequestedEvent event = EmailVerificationRequestedEvent.builder()
                .userId(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .verificationToken(rawToken)
                .expiresAt(expiresAt.toEpochMilli())
                .build();
        event.initDefaults("EMAIL_VERIFICATION_REQUESTED");
        return event;
    }

    private EmailVerifiedEvent verifiedEvent(User user) {
        EmailVerifiedEvent event = EmailVerifiedEvent.builder()
                .userId(user.getId().toString())
                .status(user.getStatus())
                .build();
        event.initDefaults("EMAIL_VERIFIED");
        return event;
    }

    private UserCommandException invalidToken() {
        return new UserCommandException(INVALID_TOKEN_CODE, INVALID_TOKEN_MESSAGE);
    }
}
