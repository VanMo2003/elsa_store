package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.CoinTransactionType;

import java.time.LocalDateTime;

/** Ví xu của khách: số dư, quy tắc (để app ghi rõ) và lịch sử mới nhất trước. */
public record CoinWalletResponse(
        int balance,
        int earnPercent,
        int maxRedeemPercent,
        PageResponse<Entry> history) {

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
