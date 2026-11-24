package com.vishesh.effectivemenu.catalog.event.infrastructure;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
import com.vishesh.effectivemenu.snapshot.cache.EffectiveMenuCache;

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
    private EffectiveMenuCache cache;

    @Autowired
    @Qualifier("testKafkaTemplate")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void cleanDatabase() {
        processedEventRepository.deleteAll();
        snapshotRepository.deleteAll();
        cache.evict(OUTLET_ID);
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
    void publishedActivation_createsUnknownItemAndReturnsItFromCustomerApi() throws Exception {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, emptyMenu(), TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), activationPayload(eventId, 5L)).join();
        kafkaTemplate.flush();

        waitForSnapshotVersion(5L);

        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotVersion").value(5))
                .andExpect(jsonPath("$.effectiveMenu.items[0].id").value(ITEM_ID))
                .andExpect(jsonPath("$.effectiveMenu.items[0].name").value("Activated Burger"))
                .andExpect(jsonPath("$.effectiveMenu.items[0].price").value(149.0))
                .andExpect(jsonPath("$.effectiveMenu.items[0].availability").value(true))
                .andExpect(jsonPath("$.effectiveMenu.items[0].visibility").value(true))
                .andExpect(jsonPath("$.effectiveMenu.items[0].status").value("ACTIVE"));
    }

    @Test
    void publishedOutletInitialization_createsFirstSnapshotAndReturnsCompleteCustomerMenu() throws Exception {
        // Unlike ordinary item events, onboarding starts with no row at all.
        // The initialization handler must create Outlet 501's first snapshot.
        String eventId = UUID.randomUUID().toString();
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "OUTLET_MENU_INITIALIZED",
                  "outletId": %d,
                  "restaurantChainId": 20,
                  "menuGroupId": 30,
                  "outletVersion": 3,
                  "outletTimezone": "UTC",
                  "occurredAt": "%s",
                  "correlationId": "outlet-onboarding-e2e-test",
                  "menuItems": [
                    {
                      "menuItemId": 101,
                      "menuItemVersion": 8,
                      "itemName": "Onboarding Burger",
                      "price": 149.00,
                      "status": "ACTIVE",
                      "availability": true,
                      "visibility": true,
                      "baseScheduleWindows": {},
                      "outletScheduleWindows": {},
                      "modifierGroups": []
                    },
                    {
                      "menuItemId": 102,
                      "menuItemVersion": 2,
                      "itemName": "Onboarding Fries",
                      "price": 79.00,
                      "status": "ACTIVE",
                      "availability": true,
                      "visibility": true,
                      "baseScheduleWindows": {},
                      "outletScheduleWindows": {},
                      "modifierGroups": []
                    }
                  ]
                }
                """.formatted(eventId, OUTLET_ID, Instant.now());

        // Catalog keys this event by outlet ID. Later Outlet 501 changes use
        // the same key, so Kafka preserves their partition order.
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(OUTLET_ID), payload).join();
        kafkaTemplate.flush();

        waitForSnapshotVersion(3L);

        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(OUTLET_ID))
                .andExpect(jsonPath("$.snapshotVersion").value(3))
                .andExpect(jsonPath("$.effectiveMenu.items.length()").value(2))
                .andExpect(jsonPath("$.effectiveMenu.items[0].id").value(101))
                .andExpect(jsonPath("$.effectiveMenu.items[0].name").value("Onboarding Burger"))
                .andExpect(jsonPath("$.effectiveMenu.items[0].price").value(149.0))
                .andExpect(jsonPath("$.effectiveMenu.items[1].id").value(102))
                .andExpect(jsonPath("$.effectiveMenu.items[1].name").value("Onboarding Fries"));
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
    void staleEvent_doesNotOverwriteNewerSnapshotOrCustomerApiResponse() throws Exception {
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

    @Test
    void publishedItemNameChange_isPersistedAndReturnedByCustomerApi() throws Exception {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
            OUTLET_ID,
            4L,
            menu(new BigDecimal("12.00"), true),
            TIME,
            TIME));

        String eventId = UUID.randomUUID().toString();
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "MENU_ITEM_CHANGED",
                  "itemId": %d,
                  "restaurantChainId": 20,
                  "outletId": %d,
                  "aggregateVersion": 5,
                  "itemName": "Veg Burger",
                  "price": 12.00,
                  "availability": true,
                  "visibility": true,
                  "status": "ACTIVE",
                  "occurredAt": "%s",
                  "correlationId": "api-e2e-test"
                }
                """.formatted(eventId, ITEM_ID, OUTLET_ID, TIME);

        kafkaTemplate.send(SOURCE_TOPIC,
            String.valueOf(ITEM_ID),
            payload).join();

        kafkaTemplate.flush();
         waitForProcessedEvent(UUID.fromString(eventId));
        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(OUTLET_ID))
                .andExpect(jsonPath("$.snapshotVersion").value(5))
                .andExpect(jsonPath("$.effectiveMenu.items[0].id").value(ITEM_ID))
                .andExpect(jsonPath("$.effectiveMenu.items[0].price").value(12.0))
                .andExpect(jsonPath("$.effectiveMenu.items[0].availability").value(true))
                .andExpect(jsonPath("$.effectiveMenu.items[0].name").value("Veg Burger"))
                .andExpect(jsonPath("$.effectiveMenu.items[0].visibility").value(true));
    }

    @Test
    void scheduleChange_updatesAvailabilityAndCustomerApiResponse() throws Exception {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(new BigDecimal("12.00"), true), TIME, TIME));

        // Use almost the whole day so the test is not tied to a particular
        // clock minute on the developer machine or CI runner.
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String activeDay = now.getDayOfWeek().name();
        LocalTime activeStart = LocalTime.MIDNIGHT;
        LocalTime activeEnd = LocalTime.of(23, 59, 59);

        String activeEventId = UUID.randomUUID().toString();
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), schedulePayload(
                activeEventId,
                5,
                activeDay,
                activeStart,
                activeEnd)).join();
        kafkaTemplate.flush();

        waitForSnapshotVersion(5L);
        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotVersion").value(5))
                .andExpect(jsonPath("$.effectiveMenu.items[0].availability").value(true));

        // A window two hours in the future is inactive at the time this event
        // is consumed, so availability must become false.
        ZonedDateTime future = now.plusHours(2);
        String inactiveEventId = UUID.randomUUID().toString();
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), schedulePayload(
                inactiveEventId,
                6,
                future.getDayOfWeek().name(),
                future.toLocalTime(),
                future.plusMinutes(30).toLocalTime())).join();
        kafkaTemplate.flush();

        waitForSnapshotVersion(6L);
        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotVersion").value(6))
                .andExpect(jsonPath("$.effectiveMenu.items[0].availability").value(false))
                .andExpect(jsonPath("$.effectiveMenu.items[0].visibility").value(true));
    }

    @Test
    void modifierChange_isPersistedAndReturnedByCustomerApi() throws Exception {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(), TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), modifierPayload(eventId, 5L, """
                [{
                  "groupId": 5,
                  "groupName": "Choose Extras",
                  "minimumSelections": 0,
                  "maximumSelections": 1,
                  "displayOrder": 0,
                  "options": [{
                    "optionId": 11,
                    "optionName": "Cheese",
                    "priceDelta": 20.00,
                    "displayOrder": 0
                  }]
                }]
                """)).join();
        kafkaTemplate.flush();

        waitForSnapshotVersion(5L);

        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveMenu.items[0].modifierGroups[0].groupId").value(5))
                .andExpect(jsonPath("$.effectiveMenu.items[0].modifierGroups[0].groupName")
                        .value("Choose Extras"))
                .andExpect(jsonPath("$.effectiveMenu.items[0].modifierGroups[0].options[0].optionName")
                        .value("Cheese"))
                .andExpect(jsonPath("$.effectiveMenu.items[0].modifierGroups[0].options[0].priceDelta")
                        .value(20.0));
    }

    @Test
    void emptyModifierChange_removesCustomerPropertyUsingOptionB() throws Exception {
        ObjectNode initialMenu = menu();
        ObjectNode item = (ObjectNode) initialMenu.get("items").get(0);
        item.putArray("modifierGroups").addObject().put("groupId", 5L);
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, initialMenu, TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), modifierPayload(eventId, 5L, "[]")).join();
        kafkaTemplate.flush();

        waitForSnapshotVersion(5L);

        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveMenu.items[0].modifierGroups").doesNotExist());
    }

    @Test
    void bundleChange_isPersistedAndReturnedByCustomerApi() throws Exception {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(), TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), bundlePayload(eventId, 5L, true, true, "ACTIVE"))
                .join();
        kafkaTemplate.flush();

        waitForSnapshotVersion(5L);

        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveMenu.items[0].name").value("Burger Meal"))
                .andExpect(jsonPath("$.effectiveMenu.items[0].price").value(299.0))
                .andExpect(jsonPath("$.effectiveMenu.items[0].bundleChildren[0].id").value(102))
                .andExpect(jsonPath("$.effectiveMenu.items[0].bundleChildren[0].name").value("Fries"))
                .andExpect(jsonPath("$.effectiveMenu.items[0].bundleChildren[0].quantity").value(1))
                .andExpect(jsonPath("$.effectiveMenu.items[0].bundleChildren[0]._bundleEligibility").doesNotExist());
    }

    @Test
    void newBundleRoot_isMaterializedEvenWhenAnotherItemHasAHigherSnapshotVersion() throws Exception {
        ObjectNode existingMenu = menu();
        ((ObjectNode) existingMenu.get("items").get(0)).put("id", 999L);
        ((ObjectNode) existingMenu.get("items").get(0)).put("name", "Existing Burger");
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, existingMenu, TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), bundlePayload(eventId, 1L, true, true, "ACTIVE"))
                .join();
        kafkaTemplate.flush();

        // The outlet snapshot stays at its maximum observed version (4), but
        // the new bundle has its own Catalog version (1) and must still appear.
        waitForProcessedEvent(UUID.fromString(eventId));

        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotVersion").value(4))
                .andExpect(jsonPath("$.effectiveMenu.items[1].id").value(ITEM_ID))
                .andExpect(jsonPath("$.effectiveMenu.items[1].name").value("Burger Meal"))
                .andExpect(jsonPath("$.effectiveMenu.items[1].bundleChildren[0].name").value("Fries"))
                .andExpect(jsonPath("$.effectiveMenu.items[1]._catalogVersion").doesNotExist());
    }

    @Test
    void bundleWithUnavailableRequiredChild_remainsVisibleButUnavailable() throws Exception {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(), TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), bundlePayload(eventId, 5L, true, false, "ACTIVE"))
                .join();
        kafkaTemplate.flush();

        waitForSnapshotVersion(5L);

        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveMenu.items[0].visibility").value(true))
                .andExpect(jsonPath("$.effectiveMenu.items[0].availability").value(false));
    }

    @Test
    void bundleWithHiddenRequiredChild_isRemovedFromCustomerApi() throws Exception {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(), TIME, TIME));

        String eventId = UUID.randomUUID().toString();
        kafkaTemplate.send(SOURCE_TOPIC, String.valueOf(ITEM_ID), bundlePayload(eventId, 5L, false, true, "HIDDEN"))
                .join();
        kafkaTemplate.flush();

        waitForSnapshotVersion(5L);

        mockMvc.perform(get("/api/v1/outlets/{outletId}/menu", OUTLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveMenu.items").isEmpty());
    }

    private String schedulePayload(String eventId, long version, String weekday,
            LocalTime startTime, LocalTime endTime) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "MENU_ITEM_CHANGED",
                  "itemId": %d,
                  "restaurantChainId": 20,
                  "outletId": %d,
                  "aggregateVersion": %d,
                  "itemName": "Burger",
                  "price": 12.00,
                  "availability": true,
                  "visibility": true,
                  "status": "ACTIVE",
                  "scheduleChanged": true,
                  "scheduleVersion": %d,
                  "scheduleTimezone": "UTC",
                  "scheduleWindows": {
                    "%s": [{"startTime": "%s", "endTime": "%s"}]
                  },
                  "occurredAt": "%s",
                  "correlationId": "schedule-e2e-test"
                }
                """.formatted(eventId, ITEM_ID, OUTLET_ID, version, version,
                        weekday, startTime, endTime, Instant.now());
    }

    private String activationPayload(String eventId, long version) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "MENU_ITEM_ACTIVATED",
                  "itemId": %d,
                  "restaurantChainId": 20,
                  "outletId": %d,
                  "aggregateVersion": %d,
                  "itemName": "Activated Burger",
                  "price": 149.00,
                  "availability": true,
                  "visibility": true,
                  "status": "ACTIVE",
                  "occurredAt": "%s",
                  "correlationId": "activation-e2e-test"
                }
                """.formatted(eventId, ITEM_ID, OUTLET_ID, version, Instant.now());
    }

    private String modifierPayload(String eventId, long version, String modifierGroupsJson) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "MODIFIER_CONFIGURATION_CHANGED",
                  "itemId": %d,
                  "restaurantChainId": 20,
                  "outletId": %d,
                  "aggregateVersion": %d,
                  "status": "ACTIVE",
                  "modifierConfigurationChanged": true,
                  "modifierGroups": %s,
                  "occurredAt": "%s",
                  "correlationId": "modifier-e2e-test"
                }
                """.formatted(eventId, ITEM_ID, OUTLET_ID, version, modifierGroupsJson, Instant.now());
    }

    private String bundlePayload(String eventId, long version, boolean childVisibility,
            boolean childAvailability, String childStatus) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "BUNDLE_CONFIGURATION_CHANGED",
                  "itemId": %d,
                  "restaurantChainId": 20,
                  "outletId": %d,
                  "aggregateVersion": %d,
                  "status": "ACTIVE",
                  "bundleConfigurationChanged": true,
                  "bundleSnapshot": {
                    "menuItemId": %d,
                    "name": "Burger Meal",
                    "price": 299.00,
                    "children": [{
                      "menuItemId": 102,
                      "name": "Fries",
                      "kind": "PRODUCT",
                      "quantity": 1,
                      "displayOrder": 0,
                      "status": "%s",
                      "visibility": %s,
                      "availability": %s,
                      "children": []
                    }]
                  },
                  "occurredAt": "%s",
                  "correlationId": "bundle-e2e-test"
                }
                """.formatted(eventId, ITEM_ID, OUTLET_ID, version, ITEM_ID, childStatus,
                        childVisibility, childAvailability, Instant.now());
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

    private ObjectNode emptyMenu() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode menu = mapper.createObjectNode();
        menu.set("items", mapper.createArrayNode());
        return menu;
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
