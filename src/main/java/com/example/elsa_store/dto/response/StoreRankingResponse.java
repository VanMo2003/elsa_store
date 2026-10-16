package com.example.elsa_store.dto.response;

/**
 * Một dòng bảng xếp hạng cửa hàng của ADMIN trong kỳ: doanh thu và số đơn hoàn thành (theo ngày hoàn thành), số đơn đặt
 * và huỷ (theo ngày đặt), điểm đánh giá trên mọi sản phẩm, thị phần doanh thu toàn sàn (0–1).
 */
public record StoreRankingResponse(
        int rank,
        Long storeId,
        String name,
        String imageUrl,
        boolean paused,
        boolean suspended,
        double revenue,
        double revenueShare,
        long completedOrders,
        long placedOrders,
        long cancelledOrders,
        double cancelRate,
        Double ratingAverage,
        long ratingCount) {}
