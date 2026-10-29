package com.vishesh.effectivemenu.catalog.event.application;

import com.vishesh.effectivemenu.catalog.event.MenuItemChangedEvent;

/** Application boundary for applying one Catalog event. */
public interface MenuItemChangedEventHandler {
    void handle(MenuItemChangedEvent event);
}
