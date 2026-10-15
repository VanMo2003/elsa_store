package com.example.elsa_store.utils;

import com.example.elsa_store.entity.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationMessagesCoinsTest {

    @Test
    void completedOrderMessageMentionsEarnedCoins() {
        Order order = new Order();
        order.setCode("ORD-1");
        order.setCoinsEarned(12_500);
        assertTrue(NotificationMessages.orderCompleted(order).body().contains("12.500 xu"));

        order.setCoinsEarned(0);
        assertFalse(NotificationMessages.orderCompleted(order).body().contains("xu"));
    }
}
