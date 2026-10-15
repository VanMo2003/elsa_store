
package com.example.elsa_store.entity;

import com.example.elsa_store.constant.NotificationType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Một dòng trong hộp thông báo của app. Cùng tiêu đề/nội dung được đẩy tới thiết bị của user qua FCM. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    /** Lưu dạng chuỗi (không dùng kiểu enum của MySQL) để thêm loại mới không phải sửa cột. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", length = 40, nullable = false)
    NotificationType type;

    @Column(name = "title", nullable = false)
    String title;

    @Column(name = "body", length = 2000)
    String body;

    /** Deep link mở khi chạm vào thông báo, ví dụ {@code elsastore://orders}. */
    @Column(name = "link")
    String link;

    /** Id bản ghi mà thông báo nói tới (id đơn hàng). */
    @Column(name = "reference_id")
    Long referenceId;

    // "read" là từ khoá của MySQL.
    @Column(name = "is_read")
    boolean read;
}
