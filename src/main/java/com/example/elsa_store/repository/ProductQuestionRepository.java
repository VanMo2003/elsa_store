package com.example.elsa_store.repository;

import com.example.elsa_store.entity.ProductQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductQuestionRepository extends JpaRepository<ProductQuestion, Long> {

    /** Hỏi đáp ở trang sản phẩm: câu đã trả lời và câu của chính người xem ({@code viewerId} null = khách vãng lai). */
    @Query("""
        select q from ProductQuestion q
        where q.product.id = :productId and (q.answer is not null or q.user.id = :viewerId)
        order by q.id desc
    """)
    Page<ProductQuestion> findForProduct(@Param("productId") Long productId,
                                         @Param("viewerId") Long viewerId,
                                         Pageable pageable);

    /** Câu hỏi về sản phẩm của một cửa hàng (màn "Câu hỏi của khách"); {@code unanswered} = chỉ câu chưa trả lời. */
    @Query("""
        select q from ProductQuestion q
        where q.product.store.id = :storeId and (:unanswered = false or q.answer is null)
        order by q.id desc
    """)
    Page<ProductQuestion> findForStore(@Param("storeId") Long storeId,
                                       @Param("unanswered") boolean unanswered,
                                       Pageable pageable);

    long countByUser_IdAndProduct_IdAndAnswerIsNull(Long userId, Long productId);

    long countByProduct_Store_IdAndAnswerIsNull(Long storeId);
}
