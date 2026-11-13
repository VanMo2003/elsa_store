package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.CustomerSegment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phân nhóm khách của cửa hàng.
 *
 * @param counts số khách mỗi nhóm (đủ 5 nhóm, kể cả 0)
 * @param customers khách thuộc nhóm đang lọc (hoặc mọi nhóm), chi nhiều nhất trước
 */
public record CustomerSegmentsResponse(Map<CustomerSegment, Long> counts, List<Item> customers) {

    /**
     * @param orders số đơn đã hoàn thành ở cửa hàng
     * @param spent tổng tiền khách trả của các đơn đó
     */
    public record Item(
            Long customerId,
            Long userId,
            String fullName,
            String phone,
            String email,
            long orders,
            double spent,
            LocalDateTime lastOrderAt,
            long daysSinceLastOrder,
            CustomerSegment segment) {}
}
