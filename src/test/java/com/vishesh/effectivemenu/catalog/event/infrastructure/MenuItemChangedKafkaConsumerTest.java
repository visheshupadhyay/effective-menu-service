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
import com.vishesh.effectivemenu.catalog.event.application.MenuItemChangedEventHandler;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MenuItemChangedKafkaConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORIGINAL_EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private MenuItemChangedEventHandler handler;

    private MenuItemChangedKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MenuItemChangedKafkaConsumer(new ObjectMapper(), handler);
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
    void blankPayload_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> consumer.consume("  "));
        verify(handler, never()).handle(any());
    }

    @Test
    void malformedJson_isRejected() {
        assertThrows(RuntimeException.class, () -> consumer.consume("{not-json"));
        verify(handler, never()).handle(any());
    }

    @Test
    void invalidDomainValue_isRejectedBeforeDelegation() {
        String payload = validPayload().replace("\"itemId\":101", "\"itemId\":-1");

        assertThrows(IllegalArgumentException.class, () -> consumer.consume(payload));
        verify(handler, never()).handle(any());
    }

    @Test
    void missingStatus_isRejectedBeforeDelegation() {
        String payload = validPayload().replace("\"status\":\"ACTIVE\",", "");

        assertThrows(IllegalArgumentException.class, () -> consumer.consume(payload));
        verify(handler, never()).handle(any());
    }

    @Test
    void unknownStatus_isRejectedBeforeDelegation() {
        String payload = validPayload().replace("\"status\":\"ACTIVE\"", "\"status\":\"UNKNOWN\"");

        assertThrows(RuntimeException.class, () -> consumer.consume(payload));
        verify(handler, never()).handle(any());
    }

    @Test
    void handlerFailure_isPropagated() {
        RuntimeException failure = new RuntimeException("handler failure");
        doThrow(failure).when(handler).handle(any());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> consumer.consume(validPayload()));

        assertEquals(failure, thrown);
        verify(handler).handle(any());
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
