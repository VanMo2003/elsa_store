package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.QuickReplyRequest;
import com.example.elsa_store.dto.response.QuickReplyResponse;
import com.example.elsa_store.entity.QuickReply;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.QuickReplyRepository;
import com.example.elsa_store.security.AccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Câu trả lời nhanh: cắt khoảng trắng, tối đa 30 mẫu mỗi cửa hàng, không đụng được mẫu của cửa hàng khác. */
class QuickReplyTest {

    private final QuickReplyRepository replies = mock(QuickReplyRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final QuickReplyServiceImpl service = new QuickReplyServiceImpl(replies, accessGuard);

    private Store mine;

    @BeforeEach
    void setUp() {
        mine = Store.builder().name("Elsa Q1").build();
        mine.setId(3L);
        when(accessGuard.requireStoreMember()).thenReturn(mine);
        when(replies.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsTrimmedRepliesUpToTheLimit() {
        QuickReplyResponse res = service.create(new QuickReplyRequest("  Chào khách ", "  Dạ shop chào bạn ạ  "));
        assertEquals("Chào khách", res.title());
        assertEquals("Dạ shop chào bạn ạ", res.content());

        when(replies.countByStore_Id(3L)).thenReturn(30L);
        assertThrows(AppException.class, () -> service.create(new QuickReplyRequest("x", "y")));
        assertThrows(AppException.class, () -> service.create(new QuickReplyRequest("   ", "y")));
    }

    @Test
    void otherStoresRepliesAreInvisible() {
        Store other = Store.builder().name("Khác").build();
        other.setId(9L);
        QuickReply theirs = QuickReply.builder().store(other).title("a").content("b").build();
        theirs.setId(5L);
        when(replies.findById(5L)).thenReturn(Optional.of(theirs));

        assertThrows(ResourceNotFoundException.class, () -> service.update(5L, new QuickReplyRequest("x", "y")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(5L));
        verify(replies, never()).delete(any());
    }
}
