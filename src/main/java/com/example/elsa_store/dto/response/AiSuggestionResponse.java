package com.example.elsa_store.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.util.List;

/** Bản nháp trả lời cho nhân viên duyệt rồi mới gửi (không có gì tới khách), hoặc câu trả lời cho khách hỏi AI. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiSuggestionResponse {
    private String content;
    /** Sản phẩm trợ lý gợi ý kèm theo (đã kiểm tra là sản phẩm thật, đang bán). */
    private List<ChatProductResponse> products;
    /** Trợ lý muốn chuyển cho nhân viên (đổi/huỷ đơn, khiếu nại, thông tin hồ sơ không có). */
    private boolean needsStaff;
    private String handoffReason;
    private List<String> toolsUsed;
}
