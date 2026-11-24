package com.vishesh.effectivemenu.catalog.event.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vishesh.effectivemenu.catalog.event.MenuItemChangedEvent;
import com.vishesh.effectivemenu.catalog.event.CatalogEventType;
import com.vishesh.effectivemenu.catalog.event.OutletMenuInitializedEvent;
import com.vishesh.effectivemenu.catalog.event.application.MenuItemChangedEventHandler;
import com.vishesh.effectivemenu.catalog.event.application.OutletMenuInitializedEventHandler;
import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MenuItemChangedKafkaConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORIGINAL_EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private MenuItemChangedEventHandler handler;

    @Mock
    private OutletMenuInitializedEventHandler outletInitializedHandler;

    private MenuItemChangedKafkaConsumer consumer;

    @Mock
    private EffectiveMenuOperationalMetrics metrics;

    @BeforeEach
    void setUp() {
        consumer = new MenuItemChangedKafkaConsumer(new ObjectMapper(), handler, outletInitializedHandler, metrics);
    }

    @Test
    void validPayload_isMappedAndDelegated() {
        consumer.consume(validPayload());

        ArgumentCaptor<MenuItemChangedEvent> captor = ArgumentCaptor.forClass(MenuItemChangedEvent.class);
        verify(handler).handle(captor.capture());

        MenuItemChangedEvent event = captor.getValue();
        assertEquals(EVENT_ID, event.getEventId());
        assertEquals(ORIGINAL_EVENT_ID, event.getOriginalEventId());
        assertEquals(101L, event.getItemId());
        assertEquals(20L, event.getRestaurantChainId());
        assertEquals(501L, event.getOutletId());
        assertEquals(4L, event.getAggregateVersion());
        assertEquals("Burger", event.getItemName());
        assertEquals(new BigDecimal("12.00"), event.getPrice());
        assertEquals(Boolean.TRUE, event.getAvailability());
        assertEquals(Boolean.TRUE, event.getVisibility());
        assertEquals(com.vishesh.effectivemenu.catalog.event.MenuItemStatus.ACTIVE, event.getStatus());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), event.getOccurredAt());
        assertEquals("corr-1", event.getCorrelationId());
    }

    @Test
    void activationPayload_isMappedAndDelegatedWithCompleteItemFields() {
        consumer.consume("""
                {
                  "eventId":"%s",
                  "eventType":"MENU_ITEM_ACTIVATED",
                  "itemId":101,
                  "restaurantChainId":20,
                  "outletId":501,
                  "aggregateVersion":4,
                  "itemName":"Burger",
                  "price":149.00,
                  "availability":true,
                  "visibility":true,
                  "status":"ACTIVE",
                  "occurredAt":"2026-01-01T00:00:00Z",
                  "correlationId":"activation-test"
                }
                """.formatted(EVENT_ID));

        ArgumentCaptor<MenuItemChangedEvent> captor = ArgumentCaptor.forClass(MenuItemChangedEvent.class);
        verify(handler).handle(captor.capture());

        MenuItemChangedEvent event = captor.getValue();
        assertEquals(CatalogEventType.MENU_ITEM_ACTIVATED, event.getEventType());
        assertEquals("Burger", event.getItemName());
        assertEquals(new BigDecimal("149.00"), event.getPrice());
        assertEquals(Boolean.TRUE, event.getAvailability());
        assertEquals(Boolean.TRUE, event.getVisibility());
        assertEquals(com.vishesh.effectivemenu.catalog.event.MenuItemStatus.ACTIVE, event.getStatus());
    }

    @Test
    void outletInitializationPayload_isMappedAndDelegatedToTheInitializationHandler() {
        consumer.consume("""
                {
                  "eventId":"%s",
                  "eventType":"OUTLET_MENU_INITIALIZED",
                  "outletId":501,
                  "restaurantChainId":20,
                  "menuGroupId":30,
                  "outletVersion":3,
                  "outletTimezone":"UTC",
                  "occurredAt":"2026-01-05T10:00:00Z",
                  "correlationId":"onboarding-1",
                  "menuItems":[{
                    "menuItemId":101,
                    "menuItemVersion":8,
                    "itemName":"Burger",
                    "price":149.00,
                    "status":"ACTIVE",
                    "availability":true,
                    "visibility":true,
                    "baseScheduleWindows":{},
                    "outletScheduleWindows":{},
                    "modifierGroups":[]
                  }]
                }
                """.formatted(EVENT_ID));

        ArgumentCaptor<OutletMenuInitializedEvent> captor = ArgumentCaptor.forClass(OutletMenuInitializedEvent.class);
        verify(outletInitializedHandler).handle(captor.capture());
        verify(handler, never()).handle(any());

        OutletMenuInitializedEvent event = captor.getValue();
        assertEquals(501L, event.getOutletId());
        assertEquals(3L, event.getOutletVersion());
        assertEquals(8L, event.getMenuItems().getFirst().menuItemVersion());
        assertEquals("Burger", event.getMenuItems().getFirst().itemName());
    }

    @Test
    void invalidOutletInitializationPayload_recordsFailureAndDoesNotDelegate() {
        String payload = """
                {
                  "eventId":"%s",
                  "eventType":"OUTLET_MENU_INITIALIZED",
                  "restaurantChainId":20,
                  "menuGroupId":30,
                  "outletVersion":3,
                  "outletTimezone":"UTC",
                  "occurredAt":"2026-01-05T10:00:00Z",
                  "menuItems":[]
                }
                """.formatted(EVENT_ID);

        assertThrows(IllegalArgumentException.class, () -> consumer.consume(payload));

        verify(outletInitializedHandler, never()).handle(any());
        verify(metrics).recordFailure();
    }

    @Test
    void modifierPayload_isMappedAndDelegated() {
        consumer.consume("""
                {
                  "eventId":"%s",
                  "eventType":"MODIFIER_CONFIGURATION_CHANGED",
                  "itemId":101,
                  "restaurantChainId":20,
                  "outletId":501,
                  "aggregateVersion":5,
                  "status":"ACTIVE",
                  "modifierConfigurationChanged":true,
                  "modifierGroups":[{
                    "groupId":5,
                    "groupName":"Choose Extras",
                    "minimumSelections":0,
                    "maximumSelections":1,
                    "displayOrder":0,
                    "options":[{
                      "optionId":11,
                      "optionName":"Cheese",
                      "priceDelta":20.00,
                      "displayOrder":0
                    }]
                  }],
                  "occurredAt":"2026-01-01T00:00:00Z",
                  "correlationId":"modifier-test"
                }
                """.formatted(EVENT_ID));

        ArgumentCaptor<MenuItemChangedEvent> captor = ArgumentCaptor.forClass(MenuItemChangedEvent.class);
        verify(handler).handle(captor.capture());
        MenuItemChangedEvent event = captor.getValue();
        assertEquals(Boolean.TRUE, event.getModifierConfigurationChanged());
        assertEquals("Choose Extras", event.getModifierGroups().getFirst().groupName());
        assertEquals("Cheese", event.getModifierGroups().getFirst().options().getFirst().optionName());
    }

    @Test
    void modifierPayloadWithoutAGroupList_isRejectedBeforeDelegation() {
        String payload = """
                {
                  "eventId":"%s",
                  "eventType":"MODIFIER_CONFIGURATION_CHANGED",
                  "itemId":101,
                  "restaurantChainId":20,
                  "outletId":501,
                  "aggregateVersion":5,
                  "status":"ACTIVE",
                  "modifierConfigurationChanged":true,
                  "occurredAt":"2026-01-01T00:00:00Z"
                }
                """.formatted(EVENT_ID);

        assertThrows(IllegalArgumentException.class, () -> consumer.consume(payload));
        verify(handler, never()).handle(any());
        verify(metrics).recordFailure();
    }

    @Test
    void bundlePayload_isMappedAndDelegated() {
        consumer.consume("""
                {
                  "eventId":"%s",
                  "eventType":"BUNDLE_CONFIGURATION_CHANGED",
                  "itemId":101,
                  "restaurantChainId":20,
                  "outletId":501,
                  "aggregateVersion":5,
                  "status":"ACTIVE",
                  "bundleConfigurationChanged":true,
                  "bundleSnapshot":{
                    "menuItemId":101,
                    "name":"Burger Meal",
                    "price":299.00,
                    "children":[{
                      "menuItemId":102,
                      "name":"Fries",
                      "kind":"PRODUCT",
                      "quantity":1,
                      "displayOrder":0,
                      "status":"ACTIVE",
                      "visibility":true,
                      "availability":true,
                      "children":[]
                    }]
                  },
                  "occurredAt":"2026-01-01T00:00:00Z",
                  "correlationId":"bundle-test"
                }
                """.formatted(EVENT_ID));

        ArgumentCaptor<MenuItemChangedEvent> captor = ArgumentCaptor.forClass(MenuItemChangedEvent.class);
        verify(handler).handle(captor.capture());
        MenuItemChangedEvent event = captor.getValue();
        assertEquals(Boolean.TRUE, event.getBundleConfigurationChanged());
        assertEquals("Burger Meal", event.getBundleSnapshot().name());
        assertEquals("Fries", event.getBundleSnapshot().children().getFirst().name());
    }

    @Test
    void bundlePayloadWithoutASnapshot_isRejectedBeforeDelegation() {
        String payload = """
                {
                  "eventId":"%s",
                  "eventType":"BUNDLE_CONFIGURATION_CHANGED",
                  "itemId":101,
                  "restaurantChainId":20,
                  "outletId":501,
                  "aggregateVersion":5,
                  "status":"ACTIVE",
                  "bundleConfigurationChanged":true,
                  "occurredAt":"2026-01-01T00:00:00Z"
                }
                """.formatted(EVENT_ID);

        assertThrows(IllegalArgumentException.class, () -> consumer.consume(payload));
        verify(handler, never()).handle(any());
        verify(metrics).recordFailure();
    }

    @Test
    void blankPayload_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> consumer.consume("  "));
        verify(handler, never()).handle(any());
        verify(metrics).recordFailure();
    }

    @Test
    void malformedJson_isRejected() {
        assertThrows(RuntimeException.class, () -> consumer.consume("{not-json"));
        verify(handler, never()).handle(any());
        verify(metrics).recordFailure();
    }

    @Test
    void invalidDomainValue_isRejectedBeforeDelegation() {
        String payload = validPayload().replace("\"itemId\":101", "\"itemId\":-1");

        assertThrows(IllegalArgumentException.class, () -> consumer.consume(payload));
        verify(handler, never()).handle(any());
        verify(metrics).recordFailure();
    }

    @Test
    void missingStatus_isRejectedBeforeDelegation() {
        String payload = validPayload().replace("\"status\":\"ACTIVE\",", "");

        assertThrows(IllegalArgumentException.class, () -> consumer.consume(payload));
        verify(handler, never()).handle(any());
        verify(metrics).recordFailure();
    }

    @Test
    void unknownStatus_isRejectedBeforeDelegation() {
        String payload = validPayload().replace("\"status\":\"ACTIVE\"", "\"status\":\"UNKNOWN\"");

        assertThrows(RuntimeException.class, () -> consumer.consume(payload));
        verify(handler, never()).handle(any());
        verify(metrics).recordFailure();
    }

    @Test
    void handlerFailure_isPropagated() {
        RuntimeException failure = new RuntimeException("handler failure");
        doThrow(failure).when(handler).handle(any());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> consumer.consume(validPayload()));

        assertEquals(failure, thrown);
        verify(handler).handle(any());
        verify(metrics).recordFailure();
    }

    private String validPayload() {
        return """
                {
                  "eventId":"%s",
                  "originalEventId":"%s",
                  "eventType":"MENU_ITEM_CHANGED",
                  "itemId":101,
                  "restaurantChainId":20,
                  "outletId":501,
                  "aggregateVersion":4,
                  "occurredAt":"2026-01-01T00:00:00Z",
                  "itemName":"Burger",
                  "price":12.00,
                  "availability":true,
                  "visibility":true,
                  "status":"ACTIVE",
                  "correlationId":"corr-1"
                }
                """.formatted(EVENT_ID, ORIGINAL_EVENT_ID);
    }
}
