package com.example.elsa_store.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Nhận xét báo cáo của một kỳ.
 *
 * @param summary 2–3 câu tóm tắt kỳ
 * @param highlights điểm nổi bật (tối đa 4)
 * @param actions việc nên làm (tối đa 4)
 * @param ai true = mô hình AI viết; false = nhận xét tự động theo quy tắc (chưa bật AI, AI bận hoặc hết lượt)
 * @param notice lý do dùng nhận xét theo quy tắc (null khi AI viết)
 */
public record ReportInsightResponse(
        String summary,
        List<String> highlights,
        List<String> actions,
        boolean ai,
        String notice,
        LocalDateTime generatedAt) {}
