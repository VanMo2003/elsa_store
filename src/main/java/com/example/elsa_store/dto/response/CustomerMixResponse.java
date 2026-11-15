package com.example.elsa_store.dto.response;

/**
 * Khách mới và khách quay lại trong kỳ (theo đơn đã hoàn thành, ngày hoàn thành). Khách mới = đơn hoàn thành đầu tiên của
 * khách (trong phạm vi cửa hàng / toàn sàn) nằm trong kỳ; khách quay lại = đã có đơn hoàn thành trước kỳ.
 *
 * @param newShare % doanh thu từ khách mới (null khi kỳ không có doanh thu)
 */
public record CustomerMixResponse(
        long newCustomers,
        long returningCustomers,
        double newRevenue,
        double returningRevenue,
        Double newShare,
        long guestOrders) {}
