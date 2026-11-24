package com.vishesh.effectivemenu.catalog.event.application;

import com.vishesh.effectivemenu.catalog.event.OutletMenuInitializedEvent;

/** Application boundary for building the first complete outlet snapshot. */
public interface OutletMenuInitializedEventHandler {
    void handle(OutletMenuInitializedEvent event);
}
