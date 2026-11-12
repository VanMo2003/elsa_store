package com.example.elsa_store.dto.response;

import java.util.List;

/**
 * Giờ vàng: đơn đặt trong kỳ (trừ đơn huỷ) theo thứ và giờ. {@code dayOfWeek} theo ISO: 1 = Thứ hai … 7 = Chủ nhật.
 *
 * @param cells chỉ những ô có đơn
 * @param byHour số đơn theo giờ 0–23 (cả tuần)
 * @param byWeekday số đơn theo thứ (chỉ số 0 = Thứ hai)
 * @param peak ô nhiều đơn nhất (null khi kỳ chưa có đơn)
 * @param peakHour giờ nhiều đơn nhất trong ngày (null khi chưa có đơn)
 * @param peakWeekday thứ nhiều đơn nhất (null khi chưa có đơn)
 */
public record SalesByTimeResponse(
        List<Cell> cells,
        List<Long> byHour,
        List<Long> byWeekday,
        long totalOrders,
        Cell peak,
        Integer peakHour,
        Integer peakWeekday) {

    public record Cell(int dayOfWeek, int hour, long orders, double revenue) {}
}
