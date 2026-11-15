package com.example.elsa_store.entity;

import com.example.elsa_store.constant.CustomerSegment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một lần cửa hàng gửi ưu đãi cho một nhóm khách (lịch sử + chặn gửi dồn dập). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "segment_campaigns", indexes = @Index(name = "idx_segment_campaign_store", columnList = "store_id, created_at"))
public class SegmentCampaign extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "segment", length = 20)
    private CustomerSegment segment;

    @Column(name = "message", length = 300)
    private String message;

    /** Mã giảm giá kèm theo (chụp lại lúc gửi); trống = chỉ lời nhắn. */
    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "recipients")
    private Integer recipients;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by")
    private User sentBy;
}
