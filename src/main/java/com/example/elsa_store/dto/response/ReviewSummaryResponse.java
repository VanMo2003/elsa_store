package com.example.elsa_store.dto.response;

import lombok.*;

import java.util.Map;

/** Tổng quan đánh giá của một sản phẩm. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSummaryResponse {
    /** Điểm trung bình, một chữ số thập phân; null khi chưa có đánh giá. */
    private Double average;
    private Long count;
    /** Số lượt theo số sao: khoá "1".."5", đủ 5 khoá. */
    private Map<String, Long> distribution;
}
