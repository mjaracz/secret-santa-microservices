package com.secretsanta.user.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Duration;

@Configuration
class NotificationTopicConfig {

    @Bean
    NewTopic notificationEventsTopic(
            @Value("${kafka.topics.notification-events}") String topicName,
            @Value("${kafka.topics.notification-retention:PT1H}") Duration retention,
            @Value("${kafka.topics.notification-partitions:1}") int partitions,
            @Value("${kafka.topics.notification-replicas:1}") short replicas
    ) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicas)
                .config(
                        TopicConfig.CLEANUP_POLICY_CONFIG,
                        TopicConfig.CLEANUP_POLICY_DELETE
                )
                .config(
                        TopicConfig.RETENTION_MS_CONFIG,
                        Long.toString(retention.toMillis())
                )
                .build();
    }
}
