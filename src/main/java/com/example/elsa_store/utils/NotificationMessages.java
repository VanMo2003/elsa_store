package com.example.elsa_store.utils;

import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.Store;

import java.text.NumberFormat;
import java.util.Locale;

/** Nội dung tiếng Việt của thông báo hiện trong app và đẩy tới điện thoại, gom về một chỗ. */
public final class NotificationMessages {

    public record Message(String title, String body) {}

    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    private NotificationMessages() {}

    /** Nút "Gửi thông báo thử": chứng minh push tới được máy này kể cả khi app chạy nền hoặc đã tắt. */
    public static Message testPush() {
        return new Message(
                "Thông báo thử",
                "Máy đã nhận được thông báo khi app chạy nền hoặc đã tắt — bạn sẽ không bỏ lỡ tin nhắn và đơn mới.");
    }

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

    public static Message orderShipping(Order order) {
        return new Message(
                "Đơn hàng đang được giao",
                "Đơn " + order.getCode() + " đã được giao cho đơn vị vận chuyển. Bạn chú ý điện thoại để nhận hàng nhé.");
    }

    public static Message orderCancelled(Order order) {
        String reason = order.getCancelReason();
        return new Message(
                "Đơn hàng đã bị huỷ",
                "Đơn " + order.getCode() + " đã bị huỷ" + (reason == null ? "." : ". Lý do: " + reason));
    }

    /** Cho cửa hàng: khách tự huỷ đơn chưa xác nhận. */
    public static Message cancelledByCustomer(Order order, String customerName) {
        String reason = order.getCancelReason();
        return new Message(
                "Khách đã huỷ đơn",
                customerName + " đã huỷ đơn " + order.getCode() + (reason == null ? "." : ". Lý do: " + reason));
    }

    /** Cho cửa hàng: khách bấm "Đã nhận được hàng". */
    public static Message receivedByCustomer(Order order, String customerName) {
        return new Message(
                "Khách đã nhận hàng",
                customerName + " đã xác nhận nhận được đơn " + order.getCode() + " ("
                        + money(order.getFinalAmount()) + ").");
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

    public static Message storeSubmitted(Store store, boolean resubmitted) {
        if (resubmitted) {
            return new Message(
                    "Hồ sơ cửa hàng được gửi lại",
                    store.getName() + " đã cập nhật hồ sơ và đang chờ xét duyệt lại.");
        }
        String owner = store.getOwnerName() == null || store.getOwnerName().isBlank()
                ? ""
                : " (" + store.getOwnerName() + ")";
        return new Message("Cửa hàng mới đăng ký", store.getName() + owner + " vừa đăng ký và đang chờ xét duyệt.");
    }

    public static Message storeApproved(Store store) {
        return new Message(
                "Cửa hàng đã được duyệt",
                "Cửa hàng " + store.getName()
                        + " đã sẵn sàng. Mở ứng dụng để thêm sản phẩm, quản lý và nhân viên.");
    }

    public static Message storeRejected(Store store, String reason) {
        String trimmed = reason.trim();
        String separator = trimmed.matches(".*[.!?…]$") ? " " : ". ";
        return new Message(
                "Hồ sơ " + store.getName() + " chưa được duyệt",
                "Lý do: " + trimmed + separator + "Cập nhật thông tin rồi gửi lại trong ứng dụng.");
    }

    /** Push cho khách: tên cửa hàng làm tiêu đề, nội dung tin làm phần thân. */
    public static Message chatToCustomer(String storeName, String content) {
        return new Message(storeName, preview(content));
    }

    /** Push cho đội ngũ cửa hàng: có cả tên khách để biết ai đang nhắn. */
    public static Message chatToStore(String customerName, String content) {
        String sender = customerName == null || customerName.isBlank() ? "Khách hàng" : customerName;
        return new Message(sender, preview(content));
    }

    /** Rút gọn về một dòng ≤ 120 ký tự để làm đoạn xem trước. */
    public static String preview(String content) {
        String flat = content.strip().replaceAll("\\s+", " ");
        return flat.length() <= 120 ? flat : flat.substring(0, 119) + "…";
    }

    /** 1200000 → "1.200.000đ". */
    public static String money(Double amount) {
        double value = amount == null ? 0 : amount;
        NumberFormat format = NumberFormat.getIntegerInstance(VI);
        return format.format(Math.round(value)) + "đ";
    }
}
