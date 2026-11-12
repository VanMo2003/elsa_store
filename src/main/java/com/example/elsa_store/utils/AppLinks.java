package com.example.elsa_store.utils;

/** Deep link mà app Flutter xử lý ({@code elsastore://...}), mở từ thông báo đẩy và email. */
public final class AppLinks {

    public static final String SCHEME = "elsastore";
    public static final String ORDERS = "orders";
    public static final String NOTIFICATIONS = "notifications";
    public static final String STORE_STATUS = "store-status";
    public static final String INVENTORY = "inventory";
    public static final String RETURNS = "returns";
    public static final String REVIEWS = "reviews";
    public static final String QUESTIONS = "questions";
    public static final String COINS = "coins";
    public static final String CART = "cart";
    public static final String VIOLATIONS = "violations";

    private AppLinks() {}

    public static String deepLink(String target) {
        return SCHEME + "://" + target;
    }

    public static String orders() {
        return deepLink(ORDERS);
    }

    public static String notifications() {
        return deepLink(NOTIFICATIONS);
    }

    /** Yêu cầu trả hàng: của khách hoặc gửi tới cửa hàng, tuỳ người mở. */
    public static String returns() {
        return deepLink(RETURNS);
    }

    /** Kho hàng của cửa hàng (lọc sẵn các biến thể sắp hết). */
    public static String inventory() {
        return deepLink(INVENTORY);
    }

    /** Đánh giá của khách về sản phẩm của cửa hàng (màn trả lời đánh giá). */
    public static String storeReviews() {
        return deepLink(REVIEWS);
    }

    /** Câu hỏi của khách về sản phẩm của cửa hàng (màn trả lời câu hỏi). */
    public static String storeQuestions() {
        return deepLink(QUESTIONS);
    }

    /** Báo cáo vi phạm chờ ADMIN xử lý. */
    public static String violations() {
        return deepLink(VIOLATIONS);
    }

    /** Giỏ hàng của khách. */
    public static String cart() {
        return deepLink(CART);
    }

    /** Ví xu và hạng thành viên của khách. */
    public static String coins() {
        return deepLink(COINS);
    }

    /** Trang một sản phẩm. */
    public static String product(Long productId) {
        return deepLink("products/" + productId);
    }

    /** Trang công khai của một cửa hàng. */
    public static String store(Long storeId) {
        return deepLink("stores/" + storeId);
    }

    /** Trạng thái duyệt cửa hàng của chủ đang đăng nhập. */
    public static String storeStatus() {
        return deepLink(STORE_STATUS);
    }

    /** Mở thẳng một cuộc trò chuyện giữa khách và cửa hàng. */
    public static String chat(Long conversationId) {
        return deepLink("chat/" + conversationId);
    }

    /** Hồ sơ đăng ký cửa hàng chờ ADMIN xét duyệt. */
    public static String storeRegistration(Long storeId) {
        return deepLink("store-registrations/" + storeId);
    }
}
