package com.example.elsa_store.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    /**
     * Lưu ảnh sản phẩm / biến thể và trả đường dẫn công khai dạng {@code /uploads/products/<tên>}. Chỉ nhận ảnh JPEG, PNG,
     * WEBP, GIF (nhận diện theo nội dung file); tên file do máy chủ đặt.
     */
    String storeProductImage(MultipartFile file);

    /** Như {@link #storeProductImage} nhưng lưu vào {@code /uploads/stores/} (ảnh đại diện cửa hàng). */
    String storeStoreImage(MultipartFile file);

    /** Như {@link #storeProductImage} nhưng lưu vào {@code /uploads/reviews/} (ảnh khách đính kèm đánh giá). */
    String storeReviewImage(MultipartFile file);

    /** Như {@link #storeProductImage} nhưng lưu vào {@code /uploads/chat/} (ảnh gửi trong tin nhắn). */
    String storeChatImage(MultipartFile file);
}
