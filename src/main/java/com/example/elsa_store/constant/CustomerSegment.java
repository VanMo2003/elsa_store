package com.example.elsa_store.constant;

/**
 * Nhóm khách của một cửa hàng, tính từ đơn đã hoàn thành ở cửa hàng đó (xét theo thứ tự):
 * <ol>
 *   <li>{@link #LOST}: lần mua gần nhất quá {@value #LOST_DAYS} ngày.</li>
 *   <li>{@link #VIP}: tổng chi từ {@value #VIP_SPENT} đ hoặc từ {@value #VIP_ORDERS} đơn.</li>
 *   <li>{@link #AT_RISK}: lần mua gần nhất quá {@value #AT_RISK_DAYS} ngày.</li>
 *   <li>{@link #LOYAL}: từ 2 đơn, mới mua gần đây.</li>
 *   <li>{@link #NEW}: mới mua một đơn gần đây.</li>
 * </ol>
 */
public enum CustomerSegment {
    VIP,
    LOYAL,
    NEW,
    AT_RISK,
    LOST;

    public static final double VIP_SPENT = 5_000_000;
    public static final long VIP_ORDERS = 5;
    public static final long AT_RISK_DAYS = 60;
    public static final long LOST_DAYS = 180;

    public static CustomerSegment of(long orders, double spent, long daysSinceLastOrder) {
        if (daysSinceLastOrder > LOST_DAYS) {
            return LOST;
        }
        if (spent >= VIP_SPENT || orders >= VIP_ORDERS) {
            return VIP;
        }
        if (daysSinceLastOrder > AT_RISK_DAYS) {
            return AT_RISK;
        }
        return orders >= 2 ? LOYAL : NEW;
    }
}
