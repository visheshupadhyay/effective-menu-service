package com.vishesh.effectivemenu.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    KafkaRetryDlqIntegrationTest.KafkaTestConfiguration.class,
    KafkaConsumerErrorConfiguration.class,
    KafkaDeadLetterConfiguration.class
})
class KafkaRetryDlqIntegrationTest {

    private static final String SOURCE_TOPIC = "test-menu-item-changed";
    private static final String DLQ_TOPIC = SOURCE_TOPIC + ".DLT";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.menu-item-changed-dlt", () -> DLQ_TOPIC);
    }

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Test
    void failingListenerRecordIsRetriedAndPublishedToDlq() {
        String key = "item-701-" + UUID.randomUUID();

        kafkaTemplate.send(SOURCE_TOPIC, key, "test-payload").join();
        kafkaTemplate.flush();

        try (Consumer<String, String> consumer = consumerFactory.createConsumer(
                "dlq-verification-" + UUID.randomUUID(), "dlq-test")) {
            consumer.subscribe(java.util.List.of(DLQ_TOPIC));

            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            ConsumerRecord<String, String> dlqRecord = null;

            while (System.nanoTime() < deadline && dlqRecord == null) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        dlqRecord = record;
                        break;
                    }
                }
            }

            assertNotNull(dlqRecord, "The failed record should reach the DLQ");
            assertEquals(key, dlqRecord.key());
            assertEquals("test-payload", dlqRecord.value());
        }
    }

    @Test
    void invalidEventIsRetriedAndPublishedToDlq() {
        String key = "invalid-item-" + UUID.randomUUID();
        // This payload is deliberately invalid: itemId must be positive.
        String invalidPayload = """
                {
                  "eventId": "%s",
                  "eventType": "MENU_ITEM_CHANGED",
                  "itemId": 0,
                  "restaurantChainId": 101,
                  "aggregateVersion": 5,
                  "price": 12.00,
                  "availability": true,
                  "visibility": true
                }
                """.formatted(UUID.randomUUID());

        kafkaTemplate.send(SOURCE_TOPIC, key, invalidPayload).join();
        kafkaTemplate.flush();

        try (Consumer<String, String> consumer = consumerFactory.createConsumer(
                "invalid-event-dlq-verification-" + UUID.randomUUID(), "dlq-test")) {
            consumer.subscribe(java.util.List.of(DLQ_TOPIC));

            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            ConsumerRecord<String, String> dlqRecord = null;

            while (System.nanoTime() < deadline && dlqRecord == null) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        dlqRecord = record;
                        break;
                    }
                }
            }

            assertNotNull(dlqRecord, "The invalid event should reach the DLQ");
            assertEquals(key, dlqRecord.key());
            assertEquals(invalidPayload, dlqRecord.value());
            assertTrue(dlqRecord.value().contains("\"itemId\": 0"));
        }
    }

    @EnableKafka
    @TestConfiguration(proxyBeanMethods = false)
    @Import({KafkaConsumerErrorConfiguration.class, KafkaDeadLetterConfiguration.class})
    static class KafkaTestConfiguration {

        @Bean
        ProducerFactory<String, Object> producerFactory() {
            Map<String, Object> properties = new HashMap<>();
            properties.put("bootstrap.servers", KAFKA.getBootstrapServers());
            properties.put("key.serializer", StringSerializer.class);
            properties.put("value.serializer", StringSerializer.class);
            return new DefaultKafkaProducerFactory<>(properties);
        }

        @Bean
        KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        ConsumerFactory<String, String> consumerFactory() {
            Map<String, Object> properties = new HashMap<>();
            properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            return new DefaultKafkaConsumerFactory<>(properties);
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
                ConsumerFactory<String, String> consumerFactory,
                DefaultErrorHandler errorHandler) {
            ConcurrentKafkaListenerContainerFactory<String, String> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            factory.setCommonErrorHandler(errorHandler);
            return factory;
        }

        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }



    static class FailingListener {

        @KafkaListener(topics = SOURCE_TOPIC, groupId = "failing-test-consumer")
        void alwaysFails(String payload) {
            throw new IllegalStateException("intentional test failure");
        }
    }
}
