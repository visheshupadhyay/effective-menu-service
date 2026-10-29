package com.vishesh.effectivemenu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
/** Retries temporary Kafka processing failures before giving up. */
public class KafkaConsumerErrorConfiguration {

    /**
     * Creates the rule that controls what happens when event processing fails.
     *
     * Kafka gives the consumer one normal attempt first. The backoff below
     * adds four more attempts, with a one-second wait between attempts. This
     * gives the database or another temporary dependency time to recover.
     *
     * If all five attempts fail, DefaultErrorHandler passes the message to
     * DeadLetterPublishingRecoverer. That recoverer publishes the message to
     * the configured dead-letter topic.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        // One second between attempts and four retries after the first attempt.
        FixedBackOff backOff = new FixedBackOff(1_000L, 4L);

        // After the retries are exhausted, send the record to the DLQ helper.
        return new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);
    }
}
