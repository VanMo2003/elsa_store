package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.Notification;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.PushNotificationListener;
import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.repository.NotificationRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.service.PushNotificationSender;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StockAlertsTest {

    private final InventoryRepository inventories = mock(InventoryRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final StockAlerts alerts = new StockAlerts(inventories, notifications);

    private static ProductVariant variant(long id, String name, String color, String size) {
        ProductVariant v = ProductVariant.builder()
                .product(Product.builder().name(name).build())
                .color(color)
                .size(size)
                .build();
        v.setId(id);
        return v;
    }

    private static InventoryRepository.AlertLevel level(long variantId, int quantity, Integer min) {
        return new InventoryRepository.AlertLevel() {
            @Override
            public Long getVariantId() {
                return variantId;
            }

            @Override
            public Integer getQuantity() {
                return quantity;
            }

            @Override
            public Integer getMinQuantity() {
                return min;
            }
        };
    }

    private static OrderItem line(ProductVariant v, int quantity, boolean reserved) {
        OrderItem item = new OrderItem();
        item.setProductVariant(v);
        item.setQuantity(quantity);
        item.setStockReserved(reserved);
        return item;
    }

    @Test
    void onlyTheSaleThatCrossesTheThresholdRaisesAnAlert() {
        ProductVariant v = variant(1, "Giay", "Den", "42");
        assertEquals(3, StockAlerts.crossing(v, 3, 3, 2).remaining(), "5 -> 3, mức 3: vừa chạm ngưỡng");
        assertNull(StockAlerts.crossing(v, 2, 3, 1), "3 -> 2: đã dưới ngưỡng từ trước, không báo lại");
        assertNull(StockAlerts.crossing(v, 8, 3, 1), "còn nhiều");
        assertTrue(StockAlerts.crossing(v, 0, 3, 2).soldOut(), "về 0 luôn báo hết hàng");
        assertTrue(StockAlerts.crossing(v, 0, null, 1).soldOut(), "không đặt mức cảnh báo vẫn báo hết hàng");
        assertNull(StockAlerts.crossing(v, 4, null, 1));
    }

    @Test
    void oneNotificationPerOrderListsEveryVariantThatRanLow() {
        Store store = Store.builder().name("Elsa").build();
        store.setId(5L);
        ProductVariant shoes = variant(1, "Giay", "Den", "42");
        ProductVariant shirt = variant(2, "Ao", null, "M");
        ProductVariant untracked = variant(3, "Mu", null, null);
        when(inventories.findAlertLevels(List.of(1L, 2L))).thenReturn(List.of(level(1, 2, 3), level(2, 0, 1)));
        Order order = Order.builder().store(store)
                .items(List.of(line(shoes, 2, true), line(shirt, 1, true), line(untracked, 1, false)))
                .build();
        order.setId(77L);

        alerts.afterSale(order);

        ArgumentCaptor<NotificationMessages.Message> message = ArgumentCaptor.forClass(NotificationMessages.Message.class);
        verify(notifications).notifyStoreManagers(
                eq(store), eq(NotificationType.LOW_STOCK), message.capture(), eq(AppLinks.inventory()), eq(77L));
        assertEquals("Có sản phẩm vừa hết hàng", message.getValue().title());
        assertEquals("Giay (Den / 42) chỉ còn 2; Ao (M) đã hết hàng. Nhập thêm hàng nhé.", message.getValue().body());
    }

    @Test
    void nothingIsSentWhenNoVariantCrossedItsThreshold() {
        Store store = Store.builder().name("Elsa").build();
        store.setId(5L);
        ProductVariant shoes = variant(1, "Giay", "Den", "42");
        when(inventories.findAlertLevels(List.of(1L))).thenReturn(List.of(level(1, 9, 3)));
        alerts.afterSale(Order.builder().store(store).items(List.of(line(shoes, 1, true))).build());
        verifyNoInteractions(notifications);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stockWarningsGoToTheOwnerAndManagersButNotToStaff() {
        NotificationRepository rows = mock(NotificationRepository.class);
        UserRepository users = mock(UserRepository.class);
        NotificationServiceImpl service = new NotificationServiceImpl(rows, mock(DeviceTokenRepository.class), users,
                mock(ApplicationEventPublisher.class), mock(PushNotificationSender.class), mock(PushNotificationListener.class));
        User owner = User.builder().username("chu").role(Role.STORE_OWNER).enabled(true).build();
        owner.setId(1L);
        User manager = User.builder().username("ql").role(Role.STORE_MANAGER).enabled(true).build();
        manager.setId(2L);
        User staff = User.builder().username("nv").role(Role.STAFF).enabled(true).build();
        staff.setId(3L);
        Store store = Store.builder().name("Elsa").owner(owner).build();
        store.setId(5L);
        when(users.findAllByStore_Id(5L)).thenReturn(List.of(manager, staff));

        service.notifyStoreManagers(store, NotificationType.LOW_STOCK,
                new NotificationMessages.Message("Sắp hết", "Giay còn 2"), AppLinks.inventory(), null);

        ArgumentCaptor<Iterable<Notification>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(rows).saveAll(saved.capture());
        List<String> recipients = new ArrayList<>();
        saved.getValue().forEach(n -> recipients.add(n.getUser().getUsername()));
        assertEquals(List.of("chu", "ql"), recipients);
    }
}
