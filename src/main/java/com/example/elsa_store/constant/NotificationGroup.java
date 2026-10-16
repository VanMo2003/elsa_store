package com.example.elsa_store.constant;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Nhóm thông báo người dùng tự tắt được ở Cài đặt (tắt thì không tạo thông báo trong app lẫn push). Thông báo về đơn
 * hàng, thanh toán, trả hàng, tồn kho và hồ sơ cửa hàng không nằm trong nhóm nào nên luôn được gửi.
 */
public enum NotificationGroup {
    /** Khách: sản phẩm đã thả tim vừa giảm giá. */
    PRICE_DROP(false, NotificationType.PRICE_DROP),
    /** Khách: cửa hàng đang theo dõi có sản phẩm mới. */
    NEW_PRODUCT(false, NotificationType.NEW_PRODUCT),
    /** Khách: sản phẩm đã thả tim có hàng trở lại. */
    BACK_IN_STOCK(false, NotificationType.BACK_IN_STOCK),
    /** Khách: cửa hàng trả lời đánh giá / câu hỏi của mình. */
    STORE_REPLIES(false, NotificationType.REVIEW_REPLIED, NotificationType.QUESTION_ANSWERED),
    /** Khách: còn hàng trong giỏ đã lâu chưa đặt. */
    CART_REMINDER(false, NotificationType.CART_REMINDER),
    /** Người của cửa hàng: khách viết đánh giá / đặt câu hỏi mới. */
    CUSTOMER_FEEDBACK(true, NotificationType.REVIEW_NEW, NotificationType.QUESTION_NEW);

    private final boolean forStore;
    private final Set<NotificationType> types;

    NotificationGroup(boolean forStore, NotificationType first, NotificationType... rest) {
        this.forStore = forStore;
        this.types = EnumSet.of(first, rest);
    }

    /** Nhóm chứa loại thông báo này; trống = loại luôn được gửi. */
    public static Optional<NotificationGroup> of(NotificationType type) {
        return Arrays.stream(values()).filter(g -> g.types.contains(type)).findFirst();
    }

    /** Khách thấy các nhóm của khách; chủ / quản lý / nhân viên thấy nhóm của cửa hàng; ADMIN không có nhóm nào. */
    public boolean appliesTo(Role role) {
        return role != null && (forStore ? role.isStoreRole() : role == Role.USER);
    }
}
