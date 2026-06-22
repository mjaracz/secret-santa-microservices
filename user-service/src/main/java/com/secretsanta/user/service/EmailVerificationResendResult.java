package com.secretsanta.user.service;

import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import com.secretsanta.common.user.events.EmailVerificationResentEvent;

public record EmailVerificationResendResult(
        EmailVerificationResentEvent responseEvent,
        EmailVerificationRequestedEvent notificationEvent
) {
}
