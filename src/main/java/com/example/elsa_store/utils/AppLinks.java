package com.example.elsa_store.utils;

/** Deep link mà app Flutter xử lý ({@code elsastore://...}), mở từ thông báo đẩy và email. */
public final class AppLinks {

    public static final String SCHEME = "elsastore";
    public static final String ORDERS = "orders";
    public static final String NOTIFICATIONS = "notifications";

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
}
