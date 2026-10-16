package com.example.elsa_store.repository;

import com.example.elsa_store.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    Optional<ProductReview> findByUser_IdAndProduct_Id(Long userId, Long productId);

    /** Đánh giá của một sản phẩm, mới nhất trước; {@code rating} null = mọi số sao; {@code withImages} = chỉ đánh giá có ảnh. */
    @Query("""
        select r from ProductReview r
        where r.product.id = :productId and (:rating is null or r.rating = :rating)
          and (:withImages = false or r.images is not empty)
        order by r.id desc
    """)
    Page<ProductReview> findForProduct(@Param("productId") Long productId,
                                       @Param("rating") Integer rating,
                                       @Param("withImages") boolean withImages,
                                       Pageable pageable);

    /** Như {@link #findForProduct} nhưng nhiều lượt "Hữu ích" nhất trước. */
    @Query("""
        select r from ProductReview r
        where r.product.id = :productId and (:rating is null or r.rating = :rating)
          and (:withImages = false or r.images is not empty)
        order by coalesce(r.helpfulCount, 0) desc, r.id desc
    """)
    Page<ProductReview> findForProductByHelpful(@Param("productId") Long productId,
                                                @Param("rating") Integer rating,
                                                @Param("withImages") boolean withImages,
                                                Pageable pageable);

    /** Số đánh giá có ảnh của một sản phẩm (nhãn bộ lọc "Có ảnh"). */
    @Query("select count(r) from ProductReview r where r.product.id = :productId and r.images is not empty")
    long countWithImages(@Param("productId") Long productId);

    /** Điểm trung bình và số lượt đánh giá theo từng sản phẩm — một truy vấn cho cả trang sản phẩm. */
    @Query("""
        select r.product.id, avg(r.rating), count(r.id)
        from ProductReview r
        where r.product.id in :productIds
        group by r.product.id
    """)
    List<Object[]> stats(@Param("productIds") Collection<Long> productIds);

    /** Điểm trung bình và số đánh giá trên mọi sản phẩm của một cửa hàng: một dòng {avg, count}. */
    @Query("select avg(r.rating), count(r.id) from ProductReview r where r.product.store.id = :storeId")
    List<Object[]> storeStats(@Param("storeId") Long storeId);

    /** Điểm trung bình và số đánh giá của mọi cửa hàng: mỗi dòng {storeId, avg, count}. */
    @Query("""
        select r.product.store.id, avg(r.rating), count(r.id)
        from ProductReview r
        where r.product.store is not null
        group by r.product.store.id
    """)
    List<Object[]> statsByStore();

    /** Số lượt đánh giá theo số sao của một sản phẩm. */
    @Query("select r.rating, count(r.id) from ProductReview r where r.product.id = :productId group by r.rating")
    List<Object[]> distribution(@Param("productId") Long productId);

    /** Đánh giá về sản phẩm của một cửa hàng (màn "Đánh giá của khách"). */
    @Query("""
        select r from ProductReview r
        where r.product.store.id = :storeId
          and (:rating is null or r.rating = :rating)
          and (:unreplied = false or r.storeReply is null)
        order by r.id desc
    """)
    Page<ProductReview> findForStore(@Param("storeId") Long storeId,
                                     @Param("rating") Integer rating,
                                     @Param("unreplied") boolean unreplied,
                                     Pageable pageable);

    long countByProduct_Store_IdAndStoreReplyIsNull(Long storeId);
}
