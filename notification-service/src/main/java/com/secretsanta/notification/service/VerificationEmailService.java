package com.secretsanta.notification.service;

import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class VerificationEmailService {

    private final JavaMailSender mailSender;
    private final String publicBaseUrl;
    private final String from;

    public VerificationEmailService(
            JavaMailSender mailSender,
            @Value("${app.public-base-url}") String publicBaseUrl,
            @Value("${app.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.publicBaseUrl = publicBaseUrl;
        this.from = from;
    }

    public void sendVerificationEmail(
            EmailVerificationRequestedEvent event
    ) {
        String baseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        String verificationUrl = baseUrl
                + "/api/auth/verify-email?token="
                + URLEncoder.encode(
                        event.getVerificationToken(),
                        StandardCharsets.UTF_8
                );

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.getEmail());
        message.setSubject("Verify your Secret Santa account");
        message.setText("""
                Hello %s,

                Verify your email address by opening this link:
                %s

                If you did not create this account, ignore this message.
                """.formatted(event.getName(), verificationUrl));

        mailSender.send(message);
        log.info("Email verification message sent for user ID: {}", event.getUserId());
    }
}
