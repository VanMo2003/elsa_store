package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.OrderMailEvent;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.service.OrderNotificationService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderNotificationServiceImpl implements OrderNotificationService {

    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void orderPlaced(Order order) {
        User recipient = recipientOf(order);
        if (recipient != null) {
            deliver(recipient, order, NotificationType.ORDER_PLACED, NotificationMessages.orderPlaced(order));
        }
        String customerName = customerNameOf(order, recipient);
        if (order.getStore() != null) {
            notificationService.notifyStore(
                    order.getStore(),
                    NotificationType.NEW_ORDER,
                    NotificationMessages.newOrder(order, customerName),
                    AppLinks.orders(),
                    order.getId());
        }
    }

    @Override
    public void statusChanged(Order order, OrderStatus previous, User actor) {
        OrderStatus status = order.getStatus();
        if (status == null || status == previous) {
            return;
        }
        User recipient = recipientOf(order);
        boolean byBuyer = actor != null && recipient != null && actor.getId() != null
                && actor.getId().equals(recipient.getId());
        if (byBuyer) {
            // Khách tự làm thì không báo lại cho khách; cửa hàng mới là người cần biết.
            notifyStoreOfCustomerAction(order, status, customerNameOf(order, recipient));
            return;
        }
        if (recipient == null) {
            return;
        }
        switch (status) {
            case DA_XAC_NHAN ->
                deliver(recipient, order, NotificationType.ORDER_CONFIRMED, NotificationMessages.orderConfirmed(order));
            case DANG_GIAO ->
                deliver(recipient, order, NotificationType.ORDER_SHIPPING, NotificationMessages.orderShipping(order));
            case DA_HUY ->
                deliver(recipient, order, NotificationType.ORDER_CANCELLED, NotificationMessages.orderCancelled(order));
            case HOAN_THANH ->
                deliver(recipient, order, NotificationType.ORDER_COMPLETED, NotificationMessages.orderCompleted(order));
            default -> {}
        }
    }

    private void notifyStoreOfCustomerAction(Order order, OrderStatus status, String customerName) {
        if (order.getStore() == null) {
            return;
        }
        NotificationMessages.Message message = switch (status) {
            case DA_HUY -> NotificationMessages.cancelledByCustomer(order, customerName);
            case HOAN_THANH -> NotificationMessages.receivedByCustomer(order, customerName);
            default -> null;
        };
        if (message != null) {
            NotificationType type = status == OrderStatus.DA_HUY ? NotificationType.ORDER_CANCELLED : NotificationType.ORDER_COMPLETED;
            notificationService.notifyStore(order.getStore(), type, message, AppLinks.orders(), order.getId());
        }
    }

    @Override
    public void paidAfterCancel(Order order) {
        if (order.getStore() != null) {
            notificationService.notifyStore(
                    order.getStore(),
                    NotificationType.PAYMENT_SUCCESS,
                    NotificationMessages.paidAfterCancel(order),
                    AppLinks.orders(),
                    order.getId());
        }
    }

    @Override
    public void paymentChanged(Order order, PaymentStatus previous) {
        PaymentStatus status = order.getPaymentStatus();
        if (status == null || status == previous) {
            return;
        }
        User recipient = recipientOf(order);
        if (recipient == null) {
            return;
        }
        switch (status) {
            case DA_THANH_TOAN ->
                deliver(recipient, order, NotificationType.PAYMENT_SUCCESS, NotificationMessages.paymentSuccess(order));
            case THAT_BAI ->
                deliver(recipient, order, NotificationType.PAYMENT_FAILED, NotificationMessages.paymentFailed(order));
            default -> {}
        }
    }

    /** Thông báo trong app + push, kèm email (gửi sau khi transaction commit). */
    private void deliver(User recipient, Order order, NotificationType type, NotificationMessages.Message message) {
        notificationService.notify(recipient, type, message, AppLinks.orders(), order.getId());
        eventPublisher.publishEvent(OrderMailEvent.of(type, order, recipient));
    }

    /** Người mua: tài khoản của khách hàng, nếu đơn do nhân viên tạo hộ thì lấy người tạo đơn. */
    private static User recipientOf(Order order) {
        if (order.getCustomer() != null && order.getCustomer().getUser() != null) {
            return order.getCustomer().getUser();
        }
        return order.getUser();
    }

    /** Khách vãng lai (không có tài khoản) vẫn có tên trên hồ sơ để hiện trong thông báo đơn mới. */
    private static String customerNameOf(Order order, User recipient) {
        if (order.getCustomer() != null && order.getCustomer().getFullName() != null) {
            return order.getCustomer().getFullName();
        }
        return recipient == null ? "khách hàng" : displayName(recipient);
    }

    private static String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName();
    }
}
