package com.example.elsa_store.constant;

/**
 * Hạng thành viên của khách theo tiền hàng thực trả của đơn hoàn thành trong 365 ngày gần nhất (trừ tiền đã hoàn do trả
 * hàng). Hạng cao nhận nhiều xu hơn: số xu = tỉ lệ nhận xu cơ bản × {@code coinMultiplier}%.
 */
public enum MemberTier {
    MEMBER("Thành viên", 0, 100),
    SILVER("Bạc", 2_000_000, 150),
    GOLD("Vàng", 5_000_000, 200),
    DIAMOND("Kim cương", 15_000_000, 300);

    private final String label;
    private final long minSpending;
    private final int coinMultiplier;

    MemberTier(String label, long minSpending, int coinMultiplier) {
        this.label = label;
        this.minSpending = minSpending;
        this.coinMultiplier = coinMultiplier;
    }

    public String label() {
        return label;
    }

    public long minSpending() {
        return minSpending;
    }

    /** Phần trăm so với tỉ lệ nhận xu cơ bản (100 = như thường). */
    public int coinMultiplier() {
        return coinMultiplier;
    }

    /** Hạng cao nhất mà {@code spending} đạt tới. */
    public static MemberTier forSpending(double spending) {
        MemberTier result = MEMBER;
        for (MemberTier tier : values()) {
            if (spending >= tier.minSpending) {
                result = tier;
            }
        }
        return result;
    }

    /** Hạng kế tiếp; null nếu đã cao nhất. */
    public MemberTier next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }
}
