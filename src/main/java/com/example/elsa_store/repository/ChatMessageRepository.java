package com.example.elsa_store.repository;

import com.example.elsa_store.constant.ChatSide;
import com.example.elsa_store.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversation_IdOrderByIdDesc(Long conversationId, Pageable pageable);

    List<ChatMessage> findByConversation_IdAndIdLessThanOrderByIdDesc(
            Long conversationId, Long beforeId, Pageable pageable);

    /** Có tin của cửa hàng do người thật viết (không phải trợ lý) sau tin {@code afterId} không. */
    boolean existsByConversation_IdAndIdGreaterThanAndSenderSideAndAiGeneratedIsNull(
            Long conversationId, Long afterId, ChatSide senderSide);
}
