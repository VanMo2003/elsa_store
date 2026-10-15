package com.example.elsa_store.utils;

/** Deep link mà app Flutter xử lý ({@code elsastore://...}), mở từ thông báo đẩy và email. */
public final class AppLinks {

    public static final String SCHEME = "elsastore";
    public static final String ORDERS = "orders";
    public static final String NOTIFICATIONS = "notifications";
    public static final String STORE_STATUS = "store-status";

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
