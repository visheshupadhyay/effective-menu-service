package com.vishesh.effectivemenu.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

@Configuration
/** Sends messages that cannot be processed to the dead-letter topic. */
public class KafkaDeadLetterConfiguration {

    /**
     * Creates the helper that sends a Kafka message to the dead-letter topic.
     *
     * A dead-letter topic is a safe place for messages that could not be
     * processed even after all retry attempts. Keeping the message there lets
     * an operator inspect it and replay it later instead of losing it.
     *
     * The original partition is kept so that messages continue to have a
     * predictable order when they are inspected or replayed.
     */
    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.menu-item-changed-dlt}") String deadLetterTopic) {

        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(deadLetterTopic, record.partition()));
    }
}
