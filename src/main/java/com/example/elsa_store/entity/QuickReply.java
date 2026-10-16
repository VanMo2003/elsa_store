package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

/** Câu trả lời nhanh của cửa hàng: mẫu tin nhắn hay dùng khi trả lời khách (người của cửa hàng dùng chung). */
@Entity
@Table(name = "chat_quick_replies", indexes = @Index(name = "idx_quick_reply_store", columnList = "store_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickReply extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** Tên ngắn để chọn, ví dụ "Chào khách", "Phí ship". */
    @Column(name = "title", length = 60, nullable = false)
    private String title;

    @Column(name = "content", length = 1000, nullable = false)
    private String content;

    public static final int MAX_PER_STORE = 30;
}
