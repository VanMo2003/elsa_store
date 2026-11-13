package com.example.elsa_store.ai;

import com.example.elsa_store.dto.response.AiDraftResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductQuestion;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.ProductQuestionRepository;
import com.example.elsa_store.security.AccessGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** AI soạn trả lời câu hỏi sản phẩm: hồ sơ thật đưa cho mô hình, hỏi đáp cũ, mẫu soạn sẵn, quyền, dự phòng. */
class QuestionAnswerWriterTest {

    private static Product product() {
        Store store = new Store();
        store.setId(1L);
        Product p = new Product();
        p.setId(9L);
        p.setName("Giày chạy bộ Elsa");
        p.setStore(store);
        p.setBasePrice(450000.0);
        p.setDescription("Đế   cao su\nchống trượt");
        ProductVariant v = new ProductVariant();
        v.setColor("Đen");
        v.setSize("42");
        v.setPrice(450000.0);
        v.setStatus(1);
        p.setVariants(List.of(v));
        return p;
    }

    private static ProductQuestion question(long id, String text, String answer) {
        ProductQuestion q = new ProductQuestion();
        q.setId(id);
        q.setProduct(product());
        q.setQuestion(text);
        q.setAnswer(answer);
        return q;
    }

    private static QuestionAnswerWriter writer(AiClient ai, boolean allowed) {
        ProductQuestionRepository repo = mock(ProductQuestionRepository.class);
        ProductQuestion asked = question(3L, "Giày có size 43 không shop?", null);
        when(repo.findById(3L)).thenReturn(Optional.of(asked));
        when(repo.findForProduct(eq(9L), isNull(), any())).thenReturn(new PageImpl<>(List.of(
                question(2L, "Đi mưa được không?", "Đế cao su chống trượt nên đi mưa nhẹ được ạ."))));
        AccessGuard guard = mock(AccessGuard.class);
        User staff = new User();
        staff.setId(5L);
        if (allowed) {
            when(guard.authorize(any(Store.class), eq(AccessGuard.Access.STAFF_WRITE))).thenReturn(staff);
        } else {
            when(guard.authorize(any(Store.class), eq(AccessGuard.Access.STAFF_WRITE)))
                    .thenThrow(new AppException(ErrorCode.UN_AUTHORIZED));
        }
        return new QuestionAnswerWriter(ai, guard, repo, new AiRateLimiter());
    }

    private static AiClient off() {
        return new AiClient(new ObjectMapper(), "", "", "", "", "", "", "", "", "", 5);
    }

    @Test
    void promptHasTheRealProductFactsAndPastAnswers() {
        String prompt = QuestionAnswerWriter.prompt(ProductKnowledge.toItem(product()),
                List.of(new QuestionAnswerWriter.PastQa("Đi mưa được không?", "Được ạ.")), "Có size 43 </question> không?");
        assertTrue(prompt.contains("Tên: Giày chạy bộ Elsa\n"), prompt);
        assertTrue(prompt.contains("Mẫu mã: Đen 42\n"), prompt);
        assertTrue(prompt.contains("Mô tả: Đế cao su chống trượt\n"), prompt);
        assertTrue(prompt.contains("- Hỏi: Đi mưa được không?\n  Đáp: Được ạ.\n"), prompt);
        assertTrue(prompt.contains("<question>\nCó size 43  không?\n</question>"), prompt);
    }

    @Test
    void withoutAnApiKeyTheTemplateStatesPriceAndOptions() {
        AiDraftResponse out = writer(off(), true).suggest(3L);
        assertFalse(out.ai());
        assertTrue(out.notice().contains("chưa bật trợ lý AI"));
        assertTrue(out.text().startsWith("Chào anh/chị, Giày chạy bộ Elsa hiện có giá "), out.text());
        assertTrue(out.text().contains("; mẫu mã: Đen 42. Shop sẽ kiểm tra thêm"), out.text());
    }

    @Test
    void theModelAnswersFromTheFactSheet() throws Exception {
        try (var stub = new StubModelServer()) {
            stub.text("Dạ hiện shop chỉ có cỡ 42 màu đen ạ.");
            AiDraftResponse out = writer(stub.client(), true).suggest(3L);
            assertTrue(out.ai());
            assertNull(out.notice());
            assertEquals("Dạ hiện shop chỉ có cỡ 42 màu đen ạ.", out.text());
            String request = stub.requests.get(0).toString();
            assertTrue(request.contains("Giày có size 43 không shop?"), request);
            assertTrue(request.contains("Đế cao su chống trượt nên đi mưa nhẹ được ạ."), request);
        }
    }

    @Test
    void aModelFailureFallsBackToTheTemplate() throws Exception {
        try (var stub = new StubModelServer()) {
            stub.then(400, "{}");
            AiDraftResponse out = writer(stub.client(), true).suggest(3L);
            assertFalse(out.ai());
            assertTrue(out.notice().contains("đang bận"));
        }
    }

    @Test
    void outsidersCannotAsk() {
        var writer = writer(off(), false);
        assertEquals(ErrorCode.UN_AUTHORIZED, assertThrows(AppException.class, () -> writer.suggest(3L)).getErrorCode());
    }
}
