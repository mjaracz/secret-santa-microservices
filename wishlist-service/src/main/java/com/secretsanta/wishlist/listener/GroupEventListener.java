package com.secretsanta.wishlist.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.secretsanta.common.group.events.DrawCompletedEvent;
import com.secretsanta.common.group.events.GroupCreatedEvent;
import com.secretsanta.common.group.events.GroupDeletedEvent;
import com.secretsanta.common.group.events.MemberAddedEvent;
import com.secretsanta.infrastructure.kafka.KafkaServiceBus;
import com.secretsanta.wishlist.service.GroupEventProjectionService;

@Component
public class GroupEventListener {

    private final KafkaServiceBus serviceBus;

    public GroupEventListener(
            KafkaServiceBus serviceBus,
            GroupEventProjectionService projectionService
    ) {
        this.serviceBus = serviceBus;
        serviceBus.registerEventHandler(GroupCreatedEvent.class, projectionService::apply);
        serviceBus.registerEventHandler(MemberAddedEvent.class, projectionService::apply);
        serviceBus.registerEventHandler(DrawCompletedEvent.class, projectionService::apply);
        serviceBus.registerEventHandler(GroupDeletedEvent.class, projectionService::apply);
    }

    @KafkaListener(
            topics = "${kafka.topics.group-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listen(String message) {
        serviceBus.handleEventMessage(message);
    }
}
