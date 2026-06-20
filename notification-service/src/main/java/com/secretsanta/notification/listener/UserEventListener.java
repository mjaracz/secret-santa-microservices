package com.secretsanta.notification.listener;

import com.secretsanta.common.user.events.EmailVerificationRequestedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.notification.service.VerificationEmailService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserEventListener {

    private final KafkaServiceBus serviceBus;

    public UserEventListener(
            KafkaServiceBus serviceBus,
            VerificationEmailService verificationEmailService
    ) {
        this.serviceBus = serviceBus;
        serviceBus.registerEventHandler(
                EmailVerificationRequestedEvent.class,
                verificationEmailService::sendVerificationEmail
        );
    }

    @KafkaListener(
            topics = "${kafka.topics.notification-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listen(String message) {
        serviceBus.handleEventMessage(message);
    }
}
