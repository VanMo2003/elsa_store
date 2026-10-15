
package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.ProductRequest;
import com.example.elsa_store.dto.request.ProductSearchRequest;
import com.example.elsa_store.dto.response.ProductDetailResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.dto.response.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {
    ProductResponse create(ProductRequest request, List<MultipartFile> files);
    ProductResponse update(Long id, ProductRequest request);
    void delete(Long id);
    ProductDetailResponse getById(Long id);
    List<String> uploadImages(Long roomId, List<MultipartFile> files);
    List<ProductResponse> getAll();
    List<ProductResponse> getAllByCategory(Long categoryId);

    /** Mọi sản phẩm của cửa hàng người gọi (kể cả đang ẩn) — màn quản lý của chủ / quản lý / nhân viên. */
    List<ProductResponse> getMine();

    /** Công khai: sản phẩm đang bán của một cửa hàng đã duyệt. */
    List<ProductResponse> getAllByStore(Long storeId);
    PageResponse<ProductResponse> search(ProductSearchRequest request);
}
