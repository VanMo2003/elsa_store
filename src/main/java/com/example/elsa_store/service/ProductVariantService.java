
package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.ProductVariantRequest;
import com.example.elsa_store.dto.response.ProductVariantDetailResponse;
import com.example.elsa_store.dto.response.ProductVariantResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductVariantService {
    ProductVariantResponse create(ProductVariantRequest req, List<MultipartFile> files);
    ProductVariantResponse update(Long id, ProductVariantRequest req);
    void delete(Long id);
    ProductVariantDetailResponse getById(Long id);
    List<ProductVariantResponse> getAll(Long productId);
    List<String> uploadImages(Long roomId, List<MultipartFile> files);

}
