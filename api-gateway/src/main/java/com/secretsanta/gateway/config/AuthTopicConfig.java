package com.secretsanta.gateway.config;

import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Duration;

@Configuration
class AuthTopicConfig {

    @Bean
    NewTopic authCommandsTopic(
            @Value("${kafka.topics.auth-commands}") String topicName,
            @Value("${kafka.topics.auth-retention:PT5M}") Duration retention,
            @Value("${kafka.topics.auth-partitions:1}") int partitions,
            @Value("${kafka.topics.auth-replicas:1}") short replicas
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
