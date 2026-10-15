
package com.example.elsa_store.mapper;

import com.example.elsa_store.dto.request.ProductRequest;
import com.example.elsa_store.dto.response.ProductDetailResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Product;

public class ProductMapper {

    public static Product toEntity(ProductRequest request, Category category) {
        if (request == null) return null;
        return Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .category(category)
                .status(request.getStatus() != null ? request.getStatus() : 1)
                .build();
    }

    public static void updateEntity(Product product, ProductRequest request, Category category) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setCategory(category);
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }
    }

    public static ProductResponse toResponse(Product product) {
        if (product == null) return null;
        ProductResponse res = new ProductResponse();
        res.setId(product.getId());
        res.setImageUrl(product.getImageUrl());
        res.setName(product.getName());
        res.setDescription(product.getDescription());
        res.setBasePrice(product.getBasePrice());
        res.setCategoryName(product.getCategory() != null ? product.getCategory().getName() : null);
        res.setCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        res.setStoreId(product.getStore() != null ? product.getStore().getId() : null);
        res.setStoreName(product.getStore() != null ? product.getStore().getName() : null);
        res.setStatus(product.getStatus());
        return res;
    }

    public static ProductDetailResponse toDetailResponse(Product product) {
        if (product == null) return null;
        ProductDetailResponse res = new ProductDetailResponse();
        res.setId(product.getId());
        res.setImageUrl(product.getImageUrl());
        res.setName(product.getName());
        res.setDescription(product.getDescription());
        res.setBasePrice(product.getBasePrice());
        res.setCategoryName(product.getCategory() != null ? product.getCategory().getName() : null);
        res.setCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        res.setStoreId(product.getStore() != null ? product.getStore().getId() : null);
        res.setStoreName(product.getStore() != null ? product.getStore().getName() : null);
        res.setStatus(product.getStatus());
        return res;
    }
}
