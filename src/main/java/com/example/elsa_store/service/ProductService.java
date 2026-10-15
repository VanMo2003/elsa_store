
package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.ProductRequest;
import com.example.elsa_store.dto.request.ProductSearchRequest;
import com.example.elsa_store.dto.response.ProductDetailResponse;
import com.example.elsa_store.dto.response.ProductImageResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.dto.response.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {
    ProductResponse create(ProductRequest request, List<MultipartFile> files);
    ProductResponse update(Long id, ProductRequest request);
    void delete(Long id);
    ProductDetailResponse getById(Long id);
    /** Thêm ảnh vào bộ ảnh; sản phẩm chưa có ảnh đại diện thì lấy ảnh đầu tiên. Tối đa {@code MAX_IMAGES} ảnh. */
    List<String> uploadImages(Long productId, List<MultipartFile> files);

    /** Bộ ảnh của sản phẩm (màn quản lý). */
    List<ProductImageResponse> images(Long productId);

    /** Gỡ một ảnh; nếu đó là ảnh đại diện thì ảnh còn lại đầu tiên thay vào. */
    List<ProductImageResponse> deleteImage(Long productId, Long imageId);

    /** Đặt một ảnh trong bộ ảnh làm ảnh đại diện. */
    List<ProductImageResponse> setCover(Long productId, Long imageId);
    List<ProductResponse> getAll();
    List<ProductResponse> getAllByCategory(Long categoryId);

    /** Mọi sản phẩm của cửa hàng người gọi (kể cả đang ẩn) — màn quản lý của chủ / quản lý / nhân viên. */
    List<ProductResponse> getMine();

    /** Công khai: sản phẩm đang bán của một cửa hàng đã duyệt. */
    List<ProductResponse> getAllByStore(Long storeId);

    /** Công khai: sản phẩm tương tự — cùng danh mục trước, thiếu thì lấy thêm hàng cùng cửa hàng; không gồm chính nó. */
    List<ProductResponse> related(Long productId, int size);
    PageResponse<ProductResponse> search(ProductSearchRequest request);
}
