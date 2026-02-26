package com.example.elsa_store.dto.request;

import lombok.Data;

@Data
public class ProductSearchRequest {
    private String keyword;
    private Long categoryId;

    private Double minPrice;
    private Double maxPrice;

    private String color;
    private String size;

    private Integer status; // 1 active

    private String sortBy;   // price, createdAt
    private String sortDir;  // asc, desc

    private Integer page = 0;
    private Integer sizePage = 10;
}
