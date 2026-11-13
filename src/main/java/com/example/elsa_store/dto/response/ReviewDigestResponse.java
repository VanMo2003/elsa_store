package com.example.elsa_store.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * "Khách nói gì": tóm tắt đánh giá của một sản phẩm do AI viết.
 *
 * @param pros ưu điểm khách hay khen (tối đa 3)
 * @param cons điểm khách hay phàn nàn (tối đa 3, có thể trống)
 * @param basedOn số đánh giá có nhận xét đã đọc
 * @param upToDate false = bản cũ, chưa tính các đánh giá mới nhất (AI đang tắt hoặc bận)
 */
public record ReviewDigestResponse(
        String summary, List<String> pros, List<String> cons, int basedOn, boolean upToDate, LocalDateTime generatedAt) {}
