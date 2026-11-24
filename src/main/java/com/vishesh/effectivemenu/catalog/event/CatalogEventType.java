package com.vishesh.effectivemenu.catalog.event;

/** Event types understood by the Effective Menu Service. */
/** Event names understood by the consumer. */
public enum CatalogEventType {
    /** Complete first menu snapshot for a newly activated outlet. */
    OUTLET_MENU_INITIALIZED,
    /** A complete first snapshot for an item that has just become active. */
    MENU_ITEM_ACTIVATED,
    /** A menu item changed in the Catalog Command Service. */
    MENU_ITEM_CHANGED,
    /** A menu item's recurring availability schedule changed. */
    SCHEDULE_CHANGED,
    /** A menu item's base or outlet seasonal date range changed. */
    SEASONAL_AVAILABILITY_CHANGED,
    /** A complete modifier-group configuration replaced the previous one. */
    MODIFIER_CONFIGURATION_CHANGED,
    /** A complete bundle structure replaced the previous one. */
    BUNDLE_CONFIGURATION_CHANGED
}
