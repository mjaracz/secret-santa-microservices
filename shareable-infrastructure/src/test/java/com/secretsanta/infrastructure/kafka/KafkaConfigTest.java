package com.secretsanta.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConfigTest {

    private KafkaConfig kafkaConfig;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");
    }

    @Test
    void configuresIdempotentProducerWithStrongestAcknowledgement() {
        ProducerFactory<String, String> producerFactory = kafkaConfig.producerFactory();
        Map<String, Object> properties = producerFactory.getConfigurationProperties();

        assertThat(properties)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                .containsEntry(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE)
                .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    }

    @Test
    void disablesAutoCommitAndReadsOnlyCommittedRecords() {
        ConsumerFactory<String, String> consumerFactory = kafkaConfig.consumerFactory();
        Map<String, Object> properties = consumerFactory.getConfigurationProperties();

        assertThat(properties)
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    }

    @Test
    void configuresRecordAcknowledgementAndDeadLetterErrorHandler() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        var errorHandler = kafkaConfig.kafkaErrorHandler(kafkaTemplate);
        var factory = kafkaConfig.kafkaListenerContainerFactory(errorHandler);

        assertThat(factory.getContainerProperties().getAckMode().name()).isEqualTo("RECORD");
        assertThat(errorHandler).isNotNull();
    }
}
