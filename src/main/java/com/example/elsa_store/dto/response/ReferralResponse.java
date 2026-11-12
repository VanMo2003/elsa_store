package com.example.elsa_store.dto.response;

/**
 * Giới thiệu bạn bè của khách đang đăng nhập.
 *
 * @param code mã để bạn bè nhập khi đăng ký
 * @param invited số tài khoản đã nhập mã này
 * @param rewarded số bạn đã có đơn đầu tiên đủ điều kiện (mỗi bạn một lần thưởng)
 * @param coinsEarned tổng xu đã nhận nhờ giới thiệu
 * @param referrerReward xu người giới thiệu nhận cho mỗi bạn
 * @param friendReward xu bạn được mời nhận
 * @param minOrderValue đơn hoàn thành tối thiểu (tiền hàng thực trả) để tính thưởng
 * @param referredBy tên (đã che) người đã giới thiệu mình; null = chưa có
 * @param canApply còn nhập được mã của người khác không
 */
public record ReferralResponse(
        String code,
        long invited,
        long rewarded,
        long coinsEarned,
        int referrerReward,
        int friendReward,
        long minOrderValue,
        String referredBy,
        boolean canApply) {}
