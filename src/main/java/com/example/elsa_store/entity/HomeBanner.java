package com.example.elsa_store.entity;

import com.example.elsa_store.constant.BannerLinkType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Banner băng chuyền ở đầu trang chủ do ADMIN quản lý (ảnh, chữ, liên kết, thời gian hiển thị). */
@Entity
@Table(name = "home_banners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeBanner extends BaseEntity {

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "subtitle", length = 200)
    private String subtitle;

    /** Ảnh ngang (đường dẫn /uploads/banners/...); trống thì app vẽ nền màu thương hiệu. */
    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "link_type", length = 20, nullable = false)
    @Builder.Default
    private BannerLinkType linkType = BannerLinkType.NONE;

    @Column(name = "link_value", length = 200)
    private String linkValue;

    /** Nhỏ hiện trước. */
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "active")
    private Boolean active;

    /** Hiện từ / đến (trống = không giới hạn). */
    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    public static final int MAX_BANNERS = 20;
}
