package com.secretsanta.notification.service;

import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VerificationEmailServiceTest {

    @Test
    void sendsVerificationLinkWithoutLoggingOrPersistingToken() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        VerificationEmailService service = new VerificationEmailService(
                mailSender,
                "http://localhost:8090",
                "no-reply@secret-santa.local"
        );
        EmailVerificationRequestedEvent event =
                EmailVerificationRequestedEvent.builder()
                        .userId("user-123")
                        .email("user@example.com")
                        .name("User")
                        .verificationToken("raw-token")
                        .build();

        service.sendVerificationEmail(event);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo())
                .containsExactly("user@example.com");
        assertThat(captor.getValue().getText())
                .contains("/api/auth/verify-email?token=raw-token");
    }
}
