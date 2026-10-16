package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.CoinTransactionType;
import com.example.elsa_store.constant.MemberTier;

import java.time.LocalDateTime;

/**
 * Ví xu của khách: số dư, quy tắc (để app ghi rõ), lịch sử mới nhất trước và hạng thành viên ({@code tier}, tỉ lệ nhận
 * xu thực tế {@code earnRate}%, chi tiêu 365 ngày {@code spending}, hạng kế tiếp và mức chi tiêu cần đạt).
 */
public record CoinWalletResponse(
        int balance,
        int earnPercent,
        int maxRedeemPercent,
        PageResponse<Entry> history,
        MemberTier tier,
        Double earnRate,
        long spending,
        MemberTier nextTier,
        Long nextTierSpending) {

    public record Entry(
            Long id,
            int amount,
            int balanceAfter,
            CoinTransactionType type,
            Long orderId,
            String orderCode,
            String note,
            LocalDateTime createdAt) {}
}
