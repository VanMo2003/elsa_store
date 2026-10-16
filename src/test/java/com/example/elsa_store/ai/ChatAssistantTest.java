package com.example.elsa_store.ai;

import com.example.elsa_store.dto.request.AiAskRequest;
import com.example.elsa_store.dto.response.ChatProductResponse;
import com.example.elsa_store.entity.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phần ghép lời nhắc của trợ lý: tin đính kèm sản phẩm và lịch sử của trợ lý mua sắm. */
class ChatAssistantTest {

    private static ChatProductResponse card(long id, String name) {
        return ChatProductResponse.builder().id(id).name(name).build();
    }

    @Test
    void attachedProductsAreNamedSoTheAssistantKnowsWhatTheCustomerMeans() {
        ChatMessage message = ChatMessage.builder()
                .content("Mình muốn hỏi về sản phẩm này")
                .productIds("12,15")
                .build();
        Map<Long, ChatProductResponse> products =
                Map.of(12L, card(12, "Giày Samba OG"), 15L, card(15, "Áo thun basic"));

        assertEquals(
                "Mình muốn hỏi về sản phẩm này [đính kèm sản phẩm: Giày Samba OG (mã 12), Áo thun basic (mã 15)]",
                ChatAssistant.withAttachments(message, products));
    }

    @Test
    void messagesWithoutProductsAreUnchanged() {
        ChatMessage message = ChatMessage.builder().content("Chào shop").build();

        assertEquals("Chào shop", ChatAssistant.withAttachments(message, Map.of()));
    }

    @Test
    void photoMessagesTellTheAssistantThereArePhotos() {
        ChatMessage photos = ChatMessage.builder().content("")
                .imageUrls(ChatMessage.joinImages(java.util.List.of("/uploads/chat/a.png", "/uploads/chat/b.png"))).build();
        assertEquals("[gửi 2 ảnh, trợ lý không xem được ảnh]", ChatAssistant.withAttachments(photos, Map.of()));
    }

    @Test
    void deletedProductsAreSkipped() {
        ChatMessage message = ChatMessage.builder().content("Xem cái này").productIds("99").build();

        assertEquals("Xem cái này", ChatAssistant.withAttachments(message, Map.of()));
    }

    @Test
    void shoppingAssistantKeepsOnlyTheLastSixTurnsAndEndsWithTheQuestion() {
        AiAskRequest request = new AiAskRequest();
        request.setQuestion("  còn màu đen không?  ");
        request.setHistory(java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> new AiAskRequest.Turn(i % 2 == 0 ? "assistant" : "user", "lượt " + i))
                .toList());

        List<ProductAssistant.Line> lines = ShoppingAssistant.lines(request);

        assertEquals(7, lines.size());
        assertEquals("lượt 5", lines.getFirst().text());
        assertEquals("Khách", lines.getFirst().who());
        assertEquals("Trợ lý", lines.get(1).who());
        assertEquals("còn màu đen không?", lines.getLast().text());
        assertEquals("Khách", lines.getLast().who());
    }

    @Test
    void shoppingAssistantWorksWithoutHistory() {
        AiAskRequest request = new AiAskRequest();
        request.setQuestion("gợi ý giày");

        assertEquals(1, ShoppingAssistant.lines(request).size());
    }

    @Test
    void productIdsRoundTripThroughTheMessage() {
        assertEquals("1,2,3", ChatMessage.joinProductIds(List.of(1L, 2L, 3L)));
        assertEquals(List.of(1L, 2L, 3L), ChatMessage.builder().productIds("1, 2,3").build().productIdList());
        assertTrue(ChatMessage.builder().build().productIdList().isEmpty());
        assertEquals(null, ChatMessage.joinProductIds(List.of()));
    }
}
