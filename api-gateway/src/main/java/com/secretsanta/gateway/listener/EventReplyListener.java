package com.secretsanta.gateway.listener;

import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventReplyListener {

    private final KafkaServiceBus serviceBus;

    @KafkaListener(
            topics = {"${kafka.topics.user-events}", "${kafka.topics.group-events}"},
            groupId = "${spring.kafka.consumer.group-id}")
    public void listen(String message) {
        serviceBus.handleEventMessage(message);
    }
}
