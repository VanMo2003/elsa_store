package com.example.elsa_store.utils;

import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.ProductReview;
import com.example.elsa_store.entity.ReturnRequest;
import com.example.elsa_store.entity.Store;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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

    /** Cho cửa hàng: khách vừa gửi yêu cầu trả hàng. */
    public static Message returnRequested(ReturnRequest r, String customerName) {
        return new Message(
                "Có yêu cầu trả hàng mới",
                customerName + " muốn trả hàng của đơn " + r.getOrder().getCode() + ". Lý do: " + r.getReason());
    }

    public static Message returnApproved(ReturnRequest r) {
        String note = r.getStoreNote();
        return new Message(
                "Yêu cầu trả hàng đã được duyệt",
                "Cửa hàng đồng ý cho trả hàng của đơn " + r.getOrder().getCode()
                        + (note == null ? ". Hãy gửi lại sản phẩm cho cửa hàng." : ". " + note));
    }

    public static Message returnRejected(ReturnRequest r) {
        return new Message(
                "Yêu cầu trả hàng bị từ chối",
                "Yêu cầu trả hàng của đơn " + r.getOrder().getCode() + " không được chấp nhận. Lý do: " + r.getStoreNote());
    }

    public static Message returnRefunded(ReturnRequest r) {
        return new Message(
                "Đã hoàn tiền trả hàng",
                "Cửa hàng đã nhận lại hàng và hoàn " + money(r.getRefundAmount()) + " cho đơn " + r.getOrder().getCode()
                        + ".");
    }

    /** Một dòng trong cảnh báo tồn kho. */
    public record StockLine(String name, int remaining, boolean soldOut) {}

    /** Cho chủ / quản lý: các sản phẩm vừa sắp hết / hết hàng sau một đơn. */
    public static Message lowStock(List<StockLine> lines) {
        boolean anySoldOut = lines.stream().anyMatch(StockLine::soldOut);
        String body = lines.stream()
                .map(l -> l.soldOut() ? l.name() + " đã hết hàng" : l.name() + " chỉ còn " + l.remaining())
                .collect(Collectors.joining("; "));
        return new Message(anySoldOut ? "Có sản phẩm vừa hết hàng" : "Sản phẩm sắp hết hàng", body + ". Nhập thêm hàng nhé.");
    }

    /** Cho cửa hàng: khách vừa đánh giá một sản phẩm. */
    public static Message reviewNew(ProductReview review) {
        String comment = review.getComment() == null ? "" : ": \u201C" + shorten(review.getComment(), 80) + "\u201D";
        return new Message(
                "Đánh giá mới " + review.getRating() + "\u2605",
                "Khách vừa đánh giá " + review.getProduct().getName() + comment);
    }

    /** Cho khách: cửa hàng trả lời đánh giá. */
    public static Message reviewReplied(ProductReview review) {
        String store = review.getProduct().getStore() == null ? "Cửa hàng" : review.getProduct().getStore().getName();
        return new Message(
                store + " đã trả lời đánh giá của bạn",
                "Về " + review.getProduct().getName() + ": \u201C" + shorten(review.getStoreReply(), 100) + "\u201D");
    }

    private static String shorten(String text, int max) {
        String value = text.strip();
        return value.length() <= max ? value : value.substring(0, max - 1).stripTrailing() + "\u2026";
    }

    /** Cho cửa hàng: tiền về cho đơn đã huỷ. */
    public static Message paidAfterCancel(Order order) {
        return new Message(
                "Cần hoàn tiền cho khách",
                "Đơn " + order.getCode() + " đã huỷ nhưng VNPay báo khách đã thanh toán "
                        + money(order.getFinalAmount()) + ". Hãy hoàn tiền cho khách.");
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
