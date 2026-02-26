
package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.ProductRequest;
import com.example.elsa_store.dto.request.ProductSearchRequest;
import com.example.elsa_store.dto.response.ProductDetailResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {
    ProductResponse create(ProductRequest request);
    ProductResponse update(Long id, ProductRequest request);
    void delete(Long id);
    ProductDetailResponse getById(Long id);
    List<String> uploadImages(Long roomId, List<MultipartFile> files);
    List<ProductResponse> getAll();
    List<ProductResponse> getAllByCategory(Long categoryId);
    Page<ProductResponse> search(ProductSearchRequest request);
}
