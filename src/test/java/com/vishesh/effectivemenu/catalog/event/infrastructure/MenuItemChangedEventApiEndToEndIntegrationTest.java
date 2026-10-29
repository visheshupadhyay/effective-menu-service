package com.vishesh.effectivemenu.catalog.event.infrastructure;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.MockMvc;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import({ TestcontainersConfiguration.class,
        MenuItemChangedEventApiEndToEndIntegrationTest.KafkaProducerTestConfiguration.class })
public class MenuItemChangedEventApiEndToEndIntegrationTest {
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
    private MockMvc mockMvc;

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
    void publishedMenuItemChange_isPersistedAndReturnedByCustomerApi() throws Exception {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(), TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "MENU_ITEM_CHANGED",
                  "itemId": %d,
                  "restaurantChainId": 20,
                  "outletId": %d,
                  "aggregateVersion": 5,
                  "itemName": "Burger",
                  "price": 12.00,
                  "availability": false,
                  "visibility": true,
                  "status": "ACTIVE",
                  "occurredAt": "%s",
                  "correlationId": "api-e2e-test"
                }
                """.formatted(eventId, ITEM_ID, OUTLET_ID, TIME);

        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), payload).join();
        kafkaTemplate.flush();

        waitForSnapshotVersion(5L);

        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(OUTLET_ID))
                .andExpect(jsonPath("$.snapshotVersion").value(5))
                .andExpect(jsonPath("$.effectiveMenu.items[0].id").value(ITEM_ID))
                .andExpect(jsonPath("$.effectiveMenu.items[0].price").value(12.0))
                .andExpect(jsonPath("$.effectiveMenu.items[0].availability").value(false))
                .andExpect(jsonPath("$.effectiveMenu.items[0].visibility").value(true));
    }

    @Test
    void staleEvent_doesNotOverwriteNewerSnapshotOrApiResponse() throws Exception {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 5L, menu(new BigDecimal("12.00"), false), TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "MENU_ITEM_CHANGED",
                  "itemId": %d,
                  "restaurantChainId": 20,
                  "outletId": %d,
                  "aggregateVersion": 4,
                  "itemName": "Burger",
                  "price": 10.00,
                  "availability": true,
                  "visibility": true,
                  "status": "ACTIVE",
                  "occurredAt": "%s",
                  "correlationId": "api-e2e-test"
                }
                """.formatted(eventId, ITEM_ID, OUTLET_ID, TIME);

        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), payload).join();
        kafkaTemplate.flush();
        waitForProcessedEvent(UUID.fromString(eventId));
        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(OUTLET_ID))
                .andExpect(jsonPath("$.snapshotVersion").value(5))
                .andExpect(jsonPath("$.effectiveMenu.items[0].id").value(ITEM_ID))
                .andExpect(jsonPath("$.effectiveMenu.items[0].price").value(12.0))
                .andExpect(jsonPath("$.effectiveMenu.items[0].availability").value(false))
                .andExpect(jsonPath("$.effectiveMenu.items[0].visibility").value(true));
    }

    @Test
    void test() throws Exception {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(new BigDecimal("10.00"), false), TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "MENU_ITEM_CHANGED",
                  "itemId": %d,
                  "restaurantChainId": 20,
                  "outletId": %d,
                  "aggregateVersion": 5,
                  "itemName": "Burger",
                  "price": 12.00,
                  "availability": true,
                  "visibility": true,
                  "status": "ACTIVE",
                  "occurredAt": "%s",
                  "correlationId": "api-e2e-test"
                }
                """.formatted(eventId, ITEM_ID, OUTLET_ID, TIME);
        
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), payload).join();
        kafkaTemplate.flush();
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), payload).join();
        kafkaTemplate.flush();
        waitForProcessedEvent(UUID.fromString(eventId));
        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(OUTLET_ID))
                .andExpect(jsonPath("$.snapshotVersion").value(5))
                .andExpect(jsonPath("$.effectiveMenu.items[0].id").value(ITEM_ID))
                .andExpect(jsonPath("$.effectiveMenu.items[0].price").value(12.0))
                .andExpect(jsonPath("$.effectiveMenu.items[0].availability").value(true))
                .andExpect(jsonPath("$.effectiveMenu.items[0].visibility").value(true));
    }

    private void waitForSnapshotVersion(long expectedVersion) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            var snapshot = snapshotRepository.findById(OUTLET_ID);
            if (snapshot.isPresent() && snapshot.get().getSnapshotVersion() == expectedVersion) {
                return;
            }
            Thread.sleep(250L);
        }
        throw new AssertionError("Snapshot did not reach version " + expectedVersion);
    }

    private void waitForProcessedEvent(UUID eventId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (processedEventRepository.existsById(eventId)) {
                return;
            }
            Thread.sleep(250L);
        }
        throw new AssertionError("Event was not processed: " + eventId);
    }

    private ObjectNode menu() {
        return menu(new BigDecimal("10.00"), true);
    }

    private ObjectNode menu(BigDecimal price, boolean availability) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode menu = mapper.createObjectNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("id", ITEM_ID);
        item.put("name", "Burger");
        item.put("price", price);
        item.put("availability", availability);
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
