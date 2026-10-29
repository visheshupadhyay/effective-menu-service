package com.vishesh.effectivemenu.snapshot.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.vishesh.effectivemenu.TestcontainersConfiguration;
import com.vishesh.effectivemenu.catalog.event.CatalogEventType;
import com.vishesh.effectivemenu.catalog.event.MenuItemChangedEvent;
import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;
import com.vishesh.effectivemenu.catalog.event.application.MenuItemChangedEventHandler;
import com.vishesh.effectivemenu.catalog.event.persistence.ProcessedEventJpaRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MenuItemChangedEventPostgresIntegrationTest {
    private static final Long OUTLET_ID = 501L;
    private static final Long ITEM_ID = 101L;
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private EffectiveMenuSnapshotJpaRepository snapshotRepository;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;

    @Autowired
    private MenuItemChangedEventHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        processedEventRepository.deleteAll();
        snapshotRepository.deleteAll();
    }

    @Test
    void newerSparseEvent_updatesSnapshotAndPreservesUnchangedFields() {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(10.00, true, true), TIME, TIME));

        MenuItemChangedEvent event = event(5L, new BigDecimal("12.00"), null, null);
        handler.handle(event);

        EffectiveMenuSnapshotEntity saved = snapshotRepository.findById(OUTLET_ID).orElseThrow();
        ObjectNode item = (ObjectNode) saved.getEffectiveMenu().get("items").get(0);

        assertEquals(5L, saved.getSnapshotVersion());
        assertEquals(12, item.get("price").asInt());
        assertEquals(true, item.get("availability").asBoolean());
        assertEquals(true, item.get("visibility").asBoolean());
        assertTrue(processedEventRepository.existsById(event.getEventId()));
    }

    @Test
    void staleEvent_doesNotOverwriteSnapshotButIsRecorded() {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 5L, menu(12.00, true, true), TIME, TIME));

        MenuItemChangedEvent event = event(4L, new BigDecimal("8.00"), null, null);
        handler.handle(event);

        EffectiveMenuSnapshotEntity saved = snapshotRepository.findById(OUTLET_ID).orElseThrow();
        ObjectNode item = (ObjectNode) saved.getEffectiveMenu().get("items").get(0);

        assertEquals(5L, saved.getSnapshotVersion());
        assertEquals(12, item.get("price").asInt());
        assertTrue(processedEventRepository.existsById(event.getEventId()));
    }

    @Test
    void archivedEvent_removesItemFromSnapshot() {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(10.00, true, true), TIME, TIME));

        handler.handle(event(5L, null, null, null, MenuItemStatus.ARCHIVED));

        EffectiveMenuSnapshotEntity saved = snapshotRepository.findById(OUTLET_ID).orElseThrow();
        assertEquals(0, saved.getEffectiveMenu().get("items").size());
    }

    @Test
    void hiddenEvent_storesItemAsInvisible() {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(10.00, true, true), TIME, TIME));

        handler.handle(event(5L, null, true, true, MenuItemStatus.HIDDEN));

        ObjectNode item = (ObjectNode) snapshotRepository.findById(OUTLET_ID).orElseThrow()
                .getEffectiveMenu().get("items").get(0);
        assertEquals(false, item.get("visibility").asBoolean());
    }

    @Test
    void draftEvent_storesItemAsInvisible() {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(10.00, true, true), TIME, TIME));

        handler.handle(event(5L, null, true, true, MenuItemStatus.DRAFT));

        ObjectNode item = (ObjectNode) snapshotRepository.findById(OUTLET_ID).orElseThrow()
                .getEffectiveMenu().get("items").get(0);
        assertEquals(false, item.get("visibility").asBoolean());
    }

    @Test
    void activeEvent_keepsItemVisible() {
        snapshotRepository.save(new EffectiveMenuSnapshotEntity(
                OUTLET_ID, 4L, menu(10.00, true, false), TIME, TIME));

        handler.handle(event(5L, null, true, true, MenuItemStatus.ACTIVE));

        ObjectNode item = (ObjectNode) snapshotRepository.findById(OUTLET_ID).orElseThrow()
                .getEffectiveMenu().get("items").get(0);
        assertEquals(true, item.get("visibility").asBoolean());
    }

    private ObjectNode menu(double price, boolean availability, boolean visibility) {
        ObjectNode menu = objectMapper.createObjectNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", ITEM_ID);
        item.put("name", "Burger");
        item.put("price", price);
        item.put("availability", availability);
        item.put("visibility", visibility);
        menu.set("items", objectMapper.createArrayNode().add(item));
        return menu;
    }

    private MenuItemChangedEvent event(long version, BigDecimal price,
            Boolean availability, Boolean visibility) {
        return event(version, price, availability, visibility, MenuItemStatus.ACTIVE);
    }

    private MenuItemChangedEvent event(long version, BigDecimal price,
            Boolean availability, Boolean visibility, MenuItemStatus status) {
        return new MenuItemChangedEvent(UUID.randomUUID(), null,
                CatalogEventType.MENU_ITEM_CHANGED, ITEM_ID, 20L, OUTLET_ID,
                version, null, price, availability, visibility, TIME, "corr-1", status);
    }
}
