package com.example.elsa_store.dto.response;

import java.util.List;

/**
 * Trạng thái điểm danh của khách.
 *
 * @param checkedInToday hôm nay đã điểm danh chưa
 * @param streak số ngày liên tiếp hiện tại (tính cả hôm nay nếu đã điểm danh; 0 khi chuỗi đã đứt)
 * @param nextDay ngày thứ mấy của chuỗi sẽ nhận ở lần điểm danh kế tiếp (hôm nay nếu chưa điểm danh, không thì ngày mai)
 * @param nextReward số xu của lần điểm danh kế tiếp
 * @param rewards xu của ngày 1 → 7
 * @param awarded số xu vừa nhận (chỉ có ở lần điểm danh thành công)
 * @param balance số dư xu sau cùng
 */
public record CheckInResponse(
        boolean checkedInToday,
        int streak,
        int nextDay,
        int nextReward,
        List<Integer> rewards,
        Integer awarded,
        int balance) {}
