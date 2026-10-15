package com.example.elsa_store.ai;

import com.example.elsa_store.dto.request.AiAskRequest;
import com.example.elsa_store.dto.response.AiSuggestionResponse;
import com.example.elsa_store.dto.response.ChatProductResponse;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.service.impl.ChatProducts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Trợ lý mua sắm Elsa: khách hỏi một câu ("giày chạy bộ dưới 1 triệu", "áo đi biển màu sáng") và nhận lời tư vấn kèm
 * thẻ sản phẩm thật từ mọi cửa hàng đã duyệt, không cần mở cuộc trò chuyện với cửa hàng nào. Không lưu gì trên máy
 * chủ: app giữ lịch sử và gửi kèm vài lượt gần nhất.
 */
@Service
@RequiredArgsConstructor
public class ShoppingAssistant {

    private static final int MAX_HISTORY_TURNS = 6;

    private final ProductAssistant assistant;
    private final ChatProducts chatProducts;
    private final AiRateLimiter rateLimiter;

    /** @param clientKey tài khoản hoặc IP của người hỏi, để giới hạn lượt */
    public AiSuggestionResponse ask(AiAskRequest req, String clientKey) {
        if (!assistant.isEnabled()) {
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        }
        rateLimiter.check("ask:" + clientKey, 20, Duration.ofMinutes(10));
        ProductAssistant.Reply reply = assistant.generate(null, null, lines(req), ProductAssistant.Mode.GLOBAL);
        Map<Long, ChatProductResponse> products = chatProducts.load(reply.productIds());
        return AiSuggestionResponse.builder()
                .content(reply.content())
                .products(products.isEmpty() ? null : new ArrayList<>(products.values()))
                .toolsUsed(reply.toolsUsed())
                .build();
    }

    static List<ProductAssistant.Line> lines(AiAskRequest req) {
        List<AiAskRequest.Turn> turns = req.getHistory() == null ? List.of() : req.getHistory();
        List<AiAskRequest.Turn> recent = turns.subList(Math.max(0, turns.size() - MAX_HISTORY_TURNS), turns.size());
        List<ProductAssistant.Line> lines = new ArrayList<>();
        for (AiAskRequest.Turn turn : recent) {
            boolean assistantTurn = "assistant".equalsIgnoreCase(turn.getRole());
            lines.add(new ProductAssistant.Line(assistantTurn ? "Trợ lý" : "Khách", "", turn.getContent()));
        }
        lines.add(new ProductAssistant.Line("Khách", "", req.getQuestion().strip()));
        return lines;
    }
}
