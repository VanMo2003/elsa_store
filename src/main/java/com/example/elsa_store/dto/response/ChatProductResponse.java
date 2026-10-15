package com.example.elsa_store.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/** Thẻ sản phẩm đính kèm tin nhắn: đủ để vẽ một thẻ và mở trang chi tiết. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatProductResponse {
    private Long id;
    private String name;
    private String imageUrl;
    /** Giá thấp nhất trong các biến thể đang bán, hoặc giá gốc nếu chưa có biến thể. */
    private Double price;
    private String categoryName;
    private Long storeId;
    private String storeName;
    /** Sản phẩm đã bị ẩn hoặc hết bán sau khi tin được gửi. */
    private Boolean available;
}
