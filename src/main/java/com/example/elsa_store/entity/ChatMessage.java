package com.example.elsa_store.entity;

import com.example.elsa_store.constant.ChatSide;
import com.example.elsa_store.constant.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Arrays;
import java.util.List;

/**
 * Một tin nhắn trong {@link Conversation}. Người gửi lưu dạng ảnh chụp (id, tên, vai trò) không có khoá ngoại, nên lịch
 * sử vẫn đọc được sau khi tài khoản nhân viên bị xoá. Tin có thể đính kèm sản phẩm ({@code productIds}) — khách hỏi về
 * một sản phẩm, nhân viên chia sẻ sản phẩm, hoặc trợ lý AI gợi ý sản phẩm.
 */
@Entity
@Table(
        name = "chat_messages",
        indexes = @Index(name = "idx_chat_messages_conversation", columnList = "conversation_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_side", length = 10, nullable = false)
    private ChatSide senderSide;

    /** Tài khoản gửi; trống với tin do trợ lý AI viết thay cửa hàng. */
    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "sender_name", length = 100)
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", length = 30)
    private Role senderRole;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Trợ lý AI viết thay cửa hàng (không có tài khoản gửi). */
    @Column(name = "ai_generated")
    private Boolean aiGenerated;

    /** Mã các sản phẩm đính kèm, cách nhau bởi dấu phẩy ("12,15"); tối đa 3. */
    @Column(name = "product_ids", length = 100)
    private String productIds;

    /** Ảnh gửi kèm (đường dẫn /uploads/chat/..., cách nhau bởi xuống dòng); tối đa {@link #MAX_IMAGES}. */
    @Column(name = "image_urls", length = 1000)
    private String imageUrls;

    public static final int MAX_IMAGES = 4;

    public List<String> imageList() {
        if (imageUrls == null || imageUrls.isBlank()) {
            return List.of();
        }
        return Arrays.stream(imageUrls.split("\n")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    public static String joinImages(List<String> urls) {
        return urls == null || urls.isEmpty() ? null : String.join("\n", urls);
    }

    public List<Long> productIdList() {
        if (productIds == null || productIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(productIds.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    public static String joinProductIds(List<Long> ids) {
        return ids == null || ids.isEmpty()
                ? null
                : String.join(",", ids.stream().map(String::valueOf).toList());
    }
}
