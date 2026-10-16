package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.AnswerRequest;
import com.example.elsa_store.dto.request.QuestionRequest;
import com.example.elsa_store.dto.response.QuestionResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductQuestion;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.ProductQuestionRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Hỏi đáp: chỉ khách hỏi, tối đa 3 câu chờ trả lời mỗi sản phẩm; trả lời báo người hỏi; ai được xoá. */
class ProductQuestionTest {

    private final ProductQuestionRepository questions = mock(ProductQuestionRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ProductQuestionServiceImpl service =
            new ProductQuestionServiceImpl(questions, products, accessGuard, notifications);

    private User fan;
    private Store store;
    private Product shoes;

    @BeforeEach
    void setUp() {
        fan = User.builder().username("khach001").role(Role.USER).build();
        fan.setId(7L);
        store = Store.builder().name("Elsa Q1").approvalStatus(ApprovalStatus.APPROVED).build();
        store.setId(3L);
        shoes = Product.builder().name("Giày chạy").status(1).store(store).build();
        shoes.setId(10L);
        when(products.findById(10L)).thenReturn(Optional.of(shoes));
        when(accessGuard.currentUser()).thenReturn(fan);
        when(questions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void customersAskAndTheStoreHearsAboutIt() {
        QuestionResponse res = service.ask(10L, new QuestionRequest("  Size 42 có không?  "));

        assertEquals("Size 42 có không?", res.getQuestion());
        assertTrue(res.getMine());
        assertEquals("k******1", res.getAskerName());
        assertNull(res.getAnswer());
        verify(notifications).notifyStore(eq(store), eq(NotificationType.QUESTION_NEW),
                argThat(m -> m.body().contains("Giày chạy") && m.body().contains("Size 42")),
                eq("elsastore://questions"), any());
    }

    @Test
    void onlyCustomersAskAndPendingQuestionsAreCapped() {
        when(questions.countByUser_IdAndProduct_IdAndAnswerIsNull(7L, 10L)).thenReturn(3L);
        AppException capped = assertThrows(AppException.class, () -> service.ask(10L, new QuestionRequest("Còn hàng?")));
        assertEquals(ErrorCode.TOO_MANY_PENDING_QUESTIONS, capped.getErrorCode());

        User staff = User.builder().username("nv").role(Role.STAFF).build();
        when(accessGuard.currentUser()).thenReturn(staff);
        assertThrows(AppException.class, () -> service.ask(10L, new QuestionRequest("Còn hàng?")));
        verify(questions, never()).save(any());
    }

    @Test
    void answeringNotifiesTheAskerOnlyWhenTheTextChanges() {
        ProductQuestion q = ProductQuestion.builder().product(shoes).user(fan).question("Có size 42?").build();
        q.setId(5L);
        when(questions.findById(5L)).thenReturn(Optional.of(q));
        User staff = User.builder().username("nv").role(Role.STAFF).build();
        when(accessGuard.authorize(store, Access.STAFF_WRITE)).thenReturn(staff);

        QuestionResponse res = service.answer(5L, new AnswerRequest(" Có ạ "));
        assertEquals("Có ạ", res.getAnswer());
        assertEquals("Elsa Q1", res.getStoreName());
        assertSame(staff, q.getAnsweredBy());
        service.answer(5L, new AnswerRequest("Có ạ"));

        verify(notifications, times(1)).notify(eq(fan), eq(NotificationType.QUESTION_ANSWERED),
                argThat(m -> m.title().contains("Elsa Q1") && m.body().contains("Có ạ")),
                eq("elsastore://products/10"), eq(10L));
    }

    @Test
    void askerOrStoreStaffMayDelete() {
        ProductQuestion q = ProductQuestion.builder().product(shoes).user(fan).question("?").build();
        q.setId(5L);
        when(questions.findById(5L)).thenReturn(Optional.of(q));

        service.delete(5L);
        verify(accessGuard, never()).authorize(any(Store.class), any());

        User other = User.builder().username("khac").role(Role.USER).build();
        other.setId(8L);
        when(accessGuard.currentUser()).thenReturn(other);
        when(accessGuard.authorize(store, Access.STAFF_WRITE)).thenThrow(new AppException(ErrorCode.UN_AUTHORIZED));
        assertThrows(AppException.class, () -> service.delete(5L));
        verify(questions, times(1)).delete(q);
    }
}
