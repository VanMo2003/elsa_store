
package com.example.elsa_store.entity;

import com.example.elsa_store.constant.ApprovalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cửa hàng. Đồng thời là hồ sơ đăng ký của chủ cửa hàng: mang thông tin liên hệ và trạng thái xét duyệt của ADMIN.
 * Sản phẩm, đơn hàng, nhà cung cấp, khuyến mãi… đều gắn với một cửa hàng.
 */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store extends BaseEntity {

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    // Thông tin liên hệ khi đăng ký; email này nhận kết quả xét duyệt.
    @Column(name = "owner_name", length = 100)
    private String ownerName;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20)
    private ApprovalStatus approvalStatus;

    /** Lý do lần gửi trước bị từ chối — giữ lại sau khi gửi lại để người duyệt có lịch sử. */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @OneToOne
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;

    /**
     * Cửa hàng bật "trợ lý AI tự trả lời": khách nhắn mà sau một lúc chưa ai của cửa hàng trả lời thì trợ lý trả lời
     * (gợi ý sản phẩm, giải đáp từ dữ liệu thật). Trống / false = tắt.
     */
    @Column(name = "ai_auto_reply")
    private Boolean aiAutoReply;

    /** Phí giao hàng mỗi đơn khách tự đặt; trống / 0 = miễn phí giao hàng. */
    @Column(name = "shipping_fee")
    private Double shippingFee;

    /** Đơn có tiền hàng (sau giảm giá) từ mức này được miễn phí giao; trống = không có mức miễn phí. */
    @Column(name = "free_shipping_threshold")
    private Double freeShippingThreshold;

    /** Tạm nghỉ bán (nghỉ lễ…): khách vẫn xem sản phẩm nhưng không đặt được đơn; bán tại quầy vẫn được. */
    @Column(name = "paused")
    private Boolean paused;

    /** Lời nhắn cho khách khi tạm nghỉ, ví dụ "Nghỉ Tết đến 05/02". */
    @Column(name = "pause_note", length = 255)
    private String pauseNote;

    public boolean isPaused() {
        return Boolean.TRUE.equals(paused);
    }

    public boolean isApproved() {
        return approvalStatus == null || approvalStatus == ApprovalStatus.APPROVED;
    }
}
