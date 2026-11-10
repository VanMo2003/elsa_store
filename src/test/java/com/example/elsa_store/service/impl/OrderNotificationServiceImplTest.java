package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.entity.Customer;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.OrderMailEvent;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderNotificationServiceImplTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final OrderNotificationServiceImpl service = new OrderNotificationServiceImpl(notifications, events);

    private static User user(String name, String email) {
        User user = User.builder().username(name).email(email).fullName("Nguyễn Văn " + name).build();
        user.setId(7L);
        return user;
    }

    private static Order order(User buyer) {
        Product product = Product.builder().name("Giày Samba OG").build();
        ProductVariant variant =
                ProductVariant.builder().product(product).color("Trắng").size("42").build();
        OrderItem item = OrderItem.builder()
                .productVariant(variant)
                .quantity(2)
                .unitPrice(600_000.0)
                .lineTotal(1_200_000.0)
                .build();
        Order order = Order.builder()
                .code("ORD-1")
                .status(OrderStatus.CHUA_XAC_NHAN)
                .paymentStatus(PaymentStatus.CHO_THANH_TOAN)
                .finalAmount(1_200_000.0)
                .items(List.of(item))
                .build();
        order.setId(99L);
        order.setCustomer(Customer.builder().user(buyer).build());
        Store store = Store.builder().name("Elsa Store").build();
        store.setId(5L);
        order.setStore(store);
        return order;
    }

    private OrderMailEvent publishedMail() {
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        return (OrderMailEvent) published.getValue();
    }

    @Test
    void placingAnOrderNotifiesTheCustomerAndTheStaff() {
        User buyer = user("an", "an@example.com");

        service.orderPlaced(order(buyer));

        verify(notifications)
                .notify(eq(buyer), eq(NotificationType.ORDER_PLACED), any(), eq(AppLinks.orders()), eq(99L));
        // Người của cửa hàng bán (chủ, quản lý, nhân viên) nhận thông báo đơn mới.
        verify(notifications)
                .notifyStore(any(Store.class), eq(NotificationType.NEW_ORDER), any(), eq(AppLinks.orders()), eq(99L));
        OrderMailEvent mail = publishedMail();
        assertEquals(NotificationType.ORDER_PLACED, mail.type());
        assertEquals("an@example.com", mail.email());
        assertEquals("ORD-1", mail.orderCode());
        assertEquals("Giày Samba OG (Trắng, size 42)", mail.items().get(0).name());
    }

    @Test
    void ordersWithoutACustomerFallBackToTheOrderCreator() {
        User creator = user("bao", "bao@example.com");
        Order order = order(null);
        order.setCustomer(null);
        order.setUser(creator);

        service.orderPlaced(order);

        verify(notifications).notify(eq(creator), eq(NotificationType.ORDER_PLACED), any(), anyString(), anyLong());
    }

    @Test
    void statusChangesNotifyOnlyOnTheMilestonesCustomersCareAbout() {
        User buyer = user("an", "an@example.com");
        Order order = order(buyer);

        order.setStatus(OrderStatus.DA_XAC_NHAN);
        service.statusChanged(order, OrderStatus.CHUA_XAC_NHAN);
        verify(notifications)
                .notify(eq(buyer), eq(NotificationType.ORDER_CONFIRMED), any(), anyString(), anyLong());

        order.setStatus(OrderStatus.DA_HUY);
        service.statusChanged(order, OrderStatus.DA_XAC_NHAN);
        verify(notifications)
                .notify(eq(buyer), eq(NotificationType.ORDER_CANCELLED), any(), anyString(), anyLong());

        order.setStatus(OrderStatus.HOAN_THANH);
        service.statusChanged(order, OrderStatus.DA_XAC_NHAN);
        verify(notifications)
                .notify(eq(buyer), eq(NotificationType.ORDER_COMPLETED), any(), anyString(), anyLong());
    }

    @Test
    void shippingMessageNamesTheCarrierAndCodeWhenTheStoreGaveThem() {
        Order order = order(user("an", "an@example.com"));
        order.setCode("ORD-9");
        assertTrue(NotificationMessages.orderShipping(order).body().contains("giao cho đơn vị vận chuyển."));

        order.setShippingCarrier("GHN");
        order.setTrackingCode("GHN123");
        assertTrue(NotificationMessages.orderShipping(order).body().contains("giao cho GHN, mã vận đơn GHN123."));
        assertTrue(NotificationMessages.trackingUpdated(order).body().contains("GHN, mã vận đơn GHN123"));
    }

    @Test
    void newTrackingCodeIsPushedToTheCustomerWithoutEmail() {
        User buyer = user("an", "an@example.com");
        Order order = order(buyer);
        service.trackingUpdated(order);
        verifyNoInteractions(notifications);

        order.setTrackingCode("GHN123");
        service.trackingUpdated(order);
        verify(notifications)
                .notify(eq(buyer), eq(NotificationType.ORDER_SHIPPING), any(), anyString(), anyLong());
        verifyNoInteractions(events);
    }

    @Test
    void shippingAndStoreSideCancellationsTellTheCustomerWithTheReason() {
        User buyer = user("an", "an@example.com");
        User staff = User.builder().username("nv").build();
        staff.setId(8L);
        Order order = order(buyer);

        order.setStatus(OrderStatus.DANG_GIAO);
        service.statusChanged(order, OrderStatus.DA_XAC_NHAN, staff);
        verify(notifications)
                .notify(eq(buyer), eq(NotificationType.ORDER_SHIPPING), any(), anyString(), anyLong());

        order.setStatus(OrderStatus.DA_HUY);
        order.setCancelReason("Khách không nghe máy");
        service.statusChanged(order, OrderStatus.DANG_GIAO, staff);
        ArgumentCaptor<NotificationMessages.Message> message = ArgumentCaptor.forClass(NotificationMessages.Message.class);
        verify(notifications)
                .notify(eq(buyer), eq(NotificationType.ORDER_CANCELLED), message.capture(), anyString(), anyLong());
        assertTrue(message.getValue().body().contains("Lý do: Khách không nghe máy"), message.getValue().body());
        verify(notifications, never()).notifyStore(any(), any(), any(), any(), any());
    }

    @Test
    void whatTheCustomerDoesThemselvesIsToldToTheStoreInstead() {
        User buyer = user("an", "an@example.com");
        Order order = order(buyer);

        order.setStatus(OrderStatus.DA_HUY);
        order.setCancelReason("Đặt nhầm size");
        service.statusChanged(order, OrderStatus.CHUA_XAC_NHAN, buyer);
        ArgumentCaptor<NotificationMessages.Message> message = ArgumentCaptor.forClass(NotificationMessages.Message.class);
        verify(notifications).notifyStore(
                eq(order.getStore()), eq(NotificationType.ORDER_CANCELLED), message.capture(), anyString(), eq(99L));
        assertTrue(message.getValue().body().contains("Đặt nhầm size"));

        order.setStatus(OrderStatus.HOAN_THANH);
        service.statusChanged(order, OrderStatus.DANG_GIAO, buyer);
        verify(notifications).notifyStore(
                eq(order.getStore()), eq(NotificationType.ORDER_COMPLETED), any(), anyString(), eq(99L));

        // Không báo lại cho chính khách, không gửi email.
        verify(notifications, never()).notify(any(), any(), any(), any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void anUnchangedStatusIsNotNotifiedAgain() {
        Order order = order(user("an", "an@example.com"));
        order.setStatus(OrderStatus.DA_XAC_NHAN);

        service.statusChanged(order, OrderStatus.DA_XAC_NHAN);

        verifyNoInteractions(notifications, events);
    }

    @Test
    void paymentChangesNotifyOnlyOnSuccessOrFailure() {
        User buyer = user("an", "an@example.com");
        Order order = order(buyer);

        order.setPaymentStatus(PaymentStatus.DA_THANH_TOAN);
        service.paymentChanged(order, PaymentStatus.CHO_THANH_TOAN);
        verify(notifications)
                .notify(eq(buyer), eq(NotificationType.PAYMENT_SUCCESS), any(), anyString(), anyLong());

        order.setPaymentStatus(PaymentStatus.THAT_BAI);
        service.paymentChanged(order, PaymentStatus.CHO_THANH_TOAN);
        verify(notifications)
                .notify(eq(buyer), eq(NotificationType.PAYMENT_FAILED), any(), anyString(), anyLong());
    }

    @Test
    void aRepeatedVnPayCallbackDoesNotSendTheSameNewsTwice() {
        Order order = order(user("an", "an@example.com"));
        order.setPaymentStatus(PaymentStatus.DA_THANH_TOAN);

        // VNPay gọi lại khi đơn đã ở trạng thái đã thanh toán.
        service.paymentChanged(order, PaymentStatus.DA_THANH_TOAN);

        verifyNoInteractions(notifications, events);
    }

    @Test
    void movingBackToPendingPaymentSendsNothing() {
        Order order = order(user("an", "an@example.com"));
        order.setPaymentStatus(PaymentStatus.CHO_THANH_TOAN);

        service.paymentChanged(order, PaymentStatus.CHUA_THANH_TOAN);

        verify(notifications, never()).notify(any(), any(), any(), any(), any());
        assertSame(PaymentStatus.CHO_THANH_TOAN, order.getPaymentStatus());
    }
}
