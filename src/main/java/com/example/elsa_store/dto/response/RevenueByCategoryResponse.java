package com.example.elsa_store.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueByCategoryResponse {
    private Long categoryId;
    private String categoryName;
    private Long quantitySold;
    private Double revenue;
}
