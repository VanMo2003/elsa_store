package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đánh giá sản phẩm của khách. Mỗi khách một đánh giá cho mỗi sản phẩm (viết lại thì sửa đánh giá cũ); chỉ khách đã nhận
 * hàng (đơn hoàn thành có sản phẩm đó) mới viết được. Cửa hàng trả lời được một lần (sửa lại câu trả lời được).
 */
@Entity
@Table(
        name = "product_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_user_product", columnNames = {"user_id", "product_id"}),
        indexes = @Index(name = "idx_review_product", columnList = "product_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReview extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 1–5 sao. */
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    /** Phân loại đã mua, chụp lúc đánh giá: "Đen / 42". */
    @Column(name = "variant_label", length = 100)
    private String variantLabel;

    /** Mã đơn đã mua — bằng chứng "đã mua hàng". */
    @Column(name = "order_code", length = 50)
    private String orderCode;

    /** Lúc khách sửa đánh giá (null = chưa sửa); tách khỏi updatedAt vì cửa hàng trả lời cũng cập nhật dòng này. */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "store_reply", length = 1000)
    private String storeReply;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @ManyToOne
    @JoinColumn(name = "replied_by")
    private User repliedBy;

    /** Ảnh khách chụp kèm đánh giá (tối đa {@link #MAX_IMAGES}), theo thứ tự tải lên. */
    @ElementCollection
    @CollectionTable(name = "product_review_images", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", length = 255, nullable = false)
    @Builder.Default
    private List<String> images = new ArrayList<>();

    /** Số khách bấm "Hữu ích" (đếm lại từ bảng phiếu mỗi lần đổi); trống = 0. */
    @Column(name = "helpful_count")
    private Integer helpfulCount;

    public int helpful() {
        return helpfulCount == null ? 0 : helpfulCount;
    }

    public static final int MAX_IMAGES = 5;
}
