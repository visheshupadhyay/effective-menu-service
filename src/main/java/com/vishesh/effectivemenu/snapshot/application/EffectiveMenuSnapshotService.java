package com.vishesh.effectivemenu.snapshot.application;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vishesh.effectivemenu.snapshot.cache.EffectiveMenuCache;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Reads stored outlet menus and prepares the customer-safe response. */
@Service
public class EffectiveMenuSnapshotService {
    private final EffectiveMenuSnapshotRepository repository;
    private final EffectiveMenuCache cache;
    public EffectiveMenuSnapshotService(
        @Qualifier ("effectiveMenuSnapshotPersistenceAdapter") EffectiveMenuSnapshotRepository repository,
        EffectiveMenuCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public EffectiveMenuSnapshot getSnapshot(Long outletId) {
        if (outletId == null || outletId <= 0) {
            throw new IllegalArgumentException("Outlet ID must be positive");
        }
        Optional<EffectiveMenuSnapshot> optional = cache.get(outletId);

        if (optional.isPresent()) {
            return optional.get();
        }

        EffectiveMenuSnapshot snapshot = repository.findByOutletId(outletId)
                .orElseThrow(() -> new EffectiveMenuSnapshotUnavailableException(outletId));
        cache.put(outletId, snapshot);
        return snapshot;
    }

    /** Returns a customer-safe copy without hidden, unavailable, or archived items. */
    public EffectiveMenuSnapshot getCustomerSnapshot(Long outletId) {
        EffectiveMenuSnapshot snapshot = getSnapshot(outletId);
        JsonNode menuCopy = snapshot.getEffectiveMenu().deepCopy();
        if (!menuCopy.isObject() || !menuCopy.has("items") || !menuCopy.get("items").isArray()) {
            return snapshot;
        }

        ArrayNode items = (ArrayNode) menuCopy.get("items");
        for (int index = items.size() - 1; index >= 0; index--) {
            JsonNode node = items.get(index);
            if (!(node instanceof ObjectNode item) || !isCustomerVisible(item)) {
                items.remove(index);
            }
        }

        return new EffectiveMenuSnapshot(snapshot.getOutletId(), snapshot.getSnapshotVersion(),
                menuCopy, snapshot.getCalculatedAt(), snapshot.getUpdatedAt());
    }

    private boolean isCustomerVisible(ObjectNode item) {
        // Customers can still see unavailable items as sold out; visibility controls hiding.
        return item.path("visibility").asBoolean(true)
                && !"ARCHIVED".equals(item.path("status").asString());
    }
}
