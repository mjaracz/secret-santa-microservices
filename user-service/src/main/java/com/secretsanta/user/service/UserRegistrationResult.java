package com.secretsanta.user.service;

import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import com.secretsanta.common.user.events.UserCreatedEvent;

public record UserRegistrationResult(
        UserCreatedEvent userCreatedEvent,
        EmailVerificationRequestedEvent verificationRequestedEvent
) {
}
