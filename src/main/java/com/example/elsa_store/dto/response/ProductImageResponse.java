package com.example.elsa_store.dto.response;

import lombok.*;

/** Một ảnh trong bộ ảnh của sản phẩm; {@code cover} = đang là ảnh đại diện (hiện ở danh sách, giỏ, đơn). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageResponse {
    private Long id;
    private String url;
    private boolean cover;
}
