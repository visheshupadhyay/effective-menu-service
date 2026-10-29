package com.vishesh.effectivemenu.catalog.event.application;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.vishesh.effectivemenu.catalog.event.MenuItemChangedEvent;
import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotRepository;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

import jakarta.transaction.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Applies one validated Catalog event to exactly one outlet snapshot. */
@Service
public class DefaultMenuItemChangedEventHandler implements MenuItemChangedEventHandler {
    private final EffectiveMenuSnapshotRepository effectiveMenuSnapshotRepository;
    private final ProcessedEventRepository processedEventRepository;

    public DefaultMenuItemChangedEventHandler(EffectiveMenuSnapshotRepository effectiveMenuSnapshotRepository,
            ProcessedEventRepository processedEventRepository) {

        this.effectiveMenuSnapshotRepository = effectiveMenuSnapshotRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Override
    @Transactional
    public void handle(MenuItemChangedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.getOutletId() == null || event.getOutletId() <= 0) {
            throw new IllegalArgumentException("Event outlet ID must be positive");
        }

        if (processedEventRepository.exists(event.getEventId())) {
            return;
        }

        Optional<EffectiveMenuSnapshot> optional = effectiveMenuSnapshotRepository.findByOutletId(event.getOutletId());

        if (optional.isEmpty()) {
            throw new EffectiveMenuSnapshotNotFoundException(event.getOutletId());
        }

        EffectiveMenuSnapshot snapshot = optional.get();

        if (event.getAggregateVersion() <= snapshot.getSnapshotVersion()) {
            markProcessed(event);
            return;
        }

        JsonNode copiedMenu = snapshot.getEffectiveMenu().deepCopy();
        if (!copiedMenu.isObject() || !copiedMenu.has("items") || !copiedMenu.get("items").isArray()) {
            throw new IllegalArgumentException("Effective menu must contain an items array");
        }

        JsonNode itemsNode = copiedMenu.get("items");

        ArrayNode items = (ArrayNode) itemsNode;

        Long itemId = event.getItemId();

        ObjectNode item = findItem(items, itemId);

        if (item == null) {
            throw new IllegalArgumentException("Menu item is not present in the outlet snapshot");
        }

        if (event.getStatus() == MenuItemStatus.ARCHIVED) {
            removeItem(items, itemId);
        } else {
            if (event.getItemName() != null) {
            item.put("name", event.getItemName());

            }

            if (event.getPrice() != null) {
                item.put("price", event.getPrice());
            }

            if (event.getAvailability() != null) {
                item.put("availability", event.getAvailability());
            }

            if (event.getVisibility() != null) {
                item.put("visibility", event.getVisibility());
            }

            item.put("status", event.getStatus().name());
            if (event.getStatus() == MenuItemStatus.HIDDEN
                    || event.getStatus() == MenuItemStatus.DRAFT) {
                item.put("visibility", false);
            }
        }

        Instant now = Instant.now();
        effectiveMenuSnapshotRepository.save(new EffectiveMenuSnapshot(
                snapshot.getOutletId(),
                event.getAggregateVersion(),
                copiedMenu,
                snapshot.getCalculatedAt(),
                now));

        markProcessed(event);
    }

    private ObjectNode findItem(ArrayNode items, Long itemId) {
        for (JsonNode item : items) {
            if (item.isObject() && item.path("id").asLong() == itemId) {
                return (ObjectNode) item;
            }
        }
        return null;
    }

    private void removeItem(ArrayNode items, Long itemId) {
        for (int index = items.size() - 1; index >= 0; index--) {
            JsonNode item = items.get(index);
            if (item.isObject() && item.path("id").asLong() == itemId) {
                items.remove(index);
                return;
            }
        }
    }

    private void markProcessed(MenuItemChangedEvent event) {
        processedEventRepository.save(event.getEventId(), event.getEventType(), Instant.now());
    }

}
