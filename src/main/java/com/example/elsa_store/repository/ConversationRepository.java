package com.example.elsa_store.repository;

import com.example.elsa_store.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByCustomer_IdAndStore_Id(Long customerId, Long storeId);

    /**
     * Khoá hàng cuộc trò chuyện (SELECT ... FOR UPDATE) trước khi ghi tin và cập nhật bộ đếm. Không khoá thì hai giao dịch
     * cùng chèn tin (khoá chia sẻ qua khoá ngoại) rồi cùng cập nhật cuộc trò chuyện (khoá độc quyền) sẽ chặn nhau và MySQL
     * báo deadlock — gặp khi khách gõ liên tiếp trong lúc trợ lý AI đang trả lời.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findForUpdate(@Param("id") Long id);

    /** Cuộc mở ra mà chưa ai nhắn thì không hiện ở hộp thư nào. */
    Page<Conversation> findAllByCustomer_IdAndLastMessageAtIsNotNull(Long customerId, Pageable pageable);

    Page<Conversation> findAllByStore_IdAndLastMessageAtIsNotNull(Long storeId, Pageable pageable);

    /** Chỉ các mã cần thiết, đọc được mà không cần transaction (luồng nền của trợ lý AI). */
    @Query("select c.id as id, c.store.id as storeId, c.customer.id as customerId from Conversation c where c.id = :id")
    Optional<ConversationRef> findRef(@Param("id") Long id);

    interface ConversationRef {
        Long getId();

        Long getStoreId();

        Long getCustomerId();
    }

    @Query("select coalesce(sum(c.customerUnread), 0) from Conversation c where c.customer.id = :customerId")
    long sumCustomerUnread(@Param("customerId") Long customerId);

    @Query("select coalesce(sum(c.storeUnread), 0) from Conversation c where c.store.id = :storeId")
    long sumStoreUnread(@Param("storeId") Long storeId);
}
