package com.example.elsa_store.entity;

import com.example.elsa_store.constant.ChatSide;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Một cuộc trò chuyện giữa một khách (tài khoản USER) và một cửa hàng. Phía cửa hàng là hộp thư chung: chủ, quản lý
 * và mọi nhân viên của cửa hàng cùng thấy và trả lời, nên trạng thái "đã đọc" tính theo phía chứ không theo từng người.
 * Xoá khách hoặc cửa hàng thì cuộc trò chuyện và tin nhắn bị xoá theo (ràng buộc ON DELETE CASCADE ở CSDL).
 */
@Entity
@Table(
        name = "conversations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "store_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Store store;

    /** Đoạn xem trước của tin mới nhất, hiện ở danh sách hộp thư. */
    @Column(name = "last_message", length = 255)
    private String lastMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_sender_side", length = 10)
    private ChatSide lastSenderSide;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** Số tin của cửa hàng mà khách chưa mở. */
    @Column(name = "customer_unread", nullable = false)
    private int customerUnread;

    /** Số tin của khách mà chưa ai trong cửa hàng mở. */
    @Column(name = "store_unread", nullable = false)
    private int storeUnread;

    /** Trợ lý AI đã chuyển cuộc này cho nhân viên (đổi/huỷ đơn, khiếu nại…); xoá khi nhân viên trả lời. */
    @Column(name = "needs_staff")
    private Boolean needsStaff;

    @Column(name = "handoff_reason", length = 255)
    private String handoffReason;
}
