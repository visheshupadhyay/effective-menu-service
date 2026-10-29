package com.vishesh.effectivemenu.catalog.event.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.vishesh.effectivemenu.TestcontainersConfiguration;
import com.vishesh.effectivemenu.catalog.event.persistence.ProcessedEventJpaRepository;
import com.vishesh.effectivemenu.snapshot.persistence.EffectiveMenuSnapshotEntity;
import com.vishesh.effectivemenu.snapshot.persistence.EffectiveMenuSnapshotJpaRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Testcontainers
@SpringBootTest
@Import({TestcontainersConfiguration.class,
        MenuItemChangedPoisonEventPostgresKafkaIntegrationTest.KafkaProducerTestConfiguration.class})
class MenuItemChangedPoisonEventPostgresKafkaIntegrationTest {
    private static final String SOURCE_TOPIC = "catalog.menu-item.changed";
    private static final String DLQ_TOPIC = SOURCE_TOPIC + ".DLT";
    private static final long OUTLET_ID = 501L;
    private static final long ITEM_ID = 101L;
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.menu-item-changed-topic", () -> SOURCE_TOPIC);
        registry.add("app.kafka.menu-item-changed-dlt", () -> DLQ_TOPIC);
    }

    @Autowired
    private EffectiveMenuSnapshotJpaRepository snapshotRepository;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;

    @Autowired
    @Qualifier("testKafkaTemplate")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void cleanDatabase() {
        processedEventRepository.deleteAll();
        snapshotRepository.deleteAll();
    }

    @Test
    void invalidEventReachesDlqWithoutChangingSnapshot() {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(), TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        String key = "item-" + ITEM_ID + "-" + UUID.randomUUID();
        String invalidPayload = """
                {
                  "eventId": "%s",
                  "eventType": "MENU_ITEM_CHANGED",
                  "itemId": 0,
                  "restaurantChainId": 20,
                  "outletId": %d,
                  "aggregateVersion": 5,
                  "price": 12.00,
                  "availability": true,
                  "visibility": true,
                  "occurredAt": "%s",
                  "correlationId": "poison-test"
                }
                """.formatted(eventId, OUTLET_ID, TIME);

        kafkaTemplate.send(SOURCE_TOPIC, key, invalidPayload).join();
        kafkaTemplate.flush();

        ConsumerRecord<String, String> dlqRecord = pollDlq(key);
        assertNotNull(dlqRecord, "The invalid event should reach the DLQ");
        assertEquals(invalidPayload, dlqRecord.value());

        EffectiveMenuSnapshotEntity unchanged = snapshotRepository.findById(OUTLET_ID).orElseThrow();
        ObjectNode item = (ObjectNode) unchanged.getEffectiveMenu().get("items").get(0);
        assertEquals(4L, unchanged.getSnapshotVersion());
        assertEquals(10, item.get("price").asInt());
        assertEquals(true, item.get("availability").asBoolean());
        assertEquals(true, item.get("visibility").asBoolean());
    }

    @Test
    void missingStatusEventReachesDlq() {
        String key = "missing-status-" + UUID.randomUUID();
        String payload = validPayload(key).replace("                  \"status\": \"ACTIVE\",\n", "");

        kafkaTemplate.send(SOURCE_TOPIC, key, payload).join();
        kafkaTemplate.flush();

        ConsumerRecord<String, String> dlqRecord = pollDlq(key);
        assertNotNull(dlqRecord, "An event without status should reach the DLQ");
    }

    @Test
    void invalidStatusEventReachesDlq() {
        String key = "invalid-status-" + UUID.randomUUID();
        String payload = validPayload(key).replace("ACTIVE", "UNKNOWN");

        kafkaTemplate.send(SOURCE_TOPIC, key, payload).join();
        kafkaTemplate.flush();

        ConsumerRecord<String, String> dlqRecord = pollDlq(key);
        assertNotNull(dlqRecord, "An event with an invalid status should reach the DLQ");
    }

    private String validPayload(String key) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "MENU_ITEM_CHANGED",
                  "itemId": %d,
                  "restaurantChainId": 20,
                  "outletId": %d,
                  "aggregateVersion": 5,
                  "price": 12.00,
                  "availability": true,
                  "visibility": true,
                  "status": "ACTIVE",
                  "occurredAt": "%s",
                  "correlationId": "%s"
                }
                """.formatted(UUID.randomUUID(), ITEM_ID, OUTLET_ID, TIME, key);
    }

    private ConsumerRecord<String, String> pollDlq(String key) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "poison-verification-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        try (Consumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(DLQ_TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
            return null;
        }
    }

    private ObjectNode menu() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode menu = mapper.createObjectNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("id", ITEM_ID);
        item.put("name", "Burger");
        item.put("price", 10.00);
        item.put("availability", true);
        item.put("visibility", true);
        menu.set("items", mapper.createArrayNode().add(item));
        return menu;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaProducerTestConfiguration {
        @Bean
        ProducerFactory<String, Object> testProducerFactory() {
            Map<String, Object> properties = new HashMap<>();
            properties.put("bootstrap.servers", KAFKA.getBootstrapServers());
            properties.put("key.serializer", StringSerializer.class);
            properties.put("value.serializer", StringSerializer.class);
            return new DefaultKafkaProducerFactory<>(properties);
        }

        @Bean(name = "testKafkaTemplate")
        KafkaTemplate<String, Object> testKafkaTemplate(
                @Qualifier("testProducerFactory") ProducerFactory<String, Object> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }
    }
}
