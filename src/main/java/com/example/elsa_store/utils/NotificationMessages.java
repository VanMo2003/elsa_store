package com.example.elsa_store.utils;

import com.example.elsa_store.entity.Order;

import java.text.NumberFormat;
import java.util.Locale;

/** Nội dung tiếng Việt của thông báo hiện trong app và đẩy tới điện thoại, gom về một chỗ. */
public final class NotificationMessages {

    public record Message(String title, String body) {}

    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    private NotificationMessages() {}

    public static Message newOrder(Order order, String customerName) {
        return new Message(
                "Có đơn hàng mới",
                "Đơn " + order.getCode() + " của " + customerName + " (" + money(order.getFinalAmount())
                        + ") đang chờ xác nhận.");
    }

    public static Message orderPlaced(Order order) {
        return new Message(
                "Đặt hàng thành công",
                "Đơn " + order.getCode() + " trị giá " + money(order.getFinalAmount())
                        + " đã được ghi nhận và đang chờ xác nhận.");
    }

    public static Message orderConfirmed(Order order) {
        return new Message(
                "Đơn hàng đã được xác nhận",
                "Đơn " + order.getCode() + " đã được xác nhận và đang được chuẩn bị.");
    }

    public static Message orderCancelled(Order order) {
        return new Message("Đơn hàng đã bị huỷ", "Đơn " + order.getCode() + " đã bị huỷ.");
    }

    public static Message orderCompleted(Order order) {
        return new Message(
                "Đơn hàng đã hoàn thành",
                "Đơn " + order.getCode() + " đã hoàn thành. Cảm ơn bạn đã mua sắm tại Elsa Store.");
    }

    public static Message paymentSuccess(Order order) {
        return new Message(
                "Thanh toán thành công",
                "Đơn " + order.getCode() + " đã được thanh toán " + money(order.getFinalAmount()) + ".");
    }

    public static Message paymentFailed(Order order) {
        return new Message(
                "Thanh toán thất bại",
                "Thanh toán cho đơn " + order.getCode() + " chưa thành công. Bạn có thể thử thanh toán lại.");
    }

    /** 1200000 → "1.200.000đ". */
    public static String money(Double amount) {
        double value = amount == null ? 0 : amount;
        NumberFormat format = NumberFormat.getIntegerInstance(VI);
        return format.format(Math.round(value)) + "đ";
    }
}
