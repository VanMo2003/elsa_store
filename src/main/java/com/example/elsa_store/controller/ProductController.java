
package com.example.elsa_store.controller;

import com.example.elsa_store.dto.request.ProductRequest;
import com.example.elsa_store.dto.request.ProductSearchRequest;
import com.example.elsa_store.dto.response.ProductDetailResponse;
import com.example.elsa_store.dto.response.ProductImageResponse;
import com.example.elsa_store.dto.response.ProductResponse;
import com.example.elsa_store.service.ProductService;
import com.example.elsa_store.service.impl.ProductViewCounter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.PageResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private ProductViewCounter viewCounter;

    /** Đếm lượt xem trang sản phẩm (tiêm qua setter để giữ nguyên hàm dựng). */
    @Autowired
    void setViewCounter(ProductViewCounter viewCounter) {
        this.viewCounter = viewCounter;
    }

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** Tạo sản phẩm kèm ảnh: multipart, phần {@code data} là JSON, phần {@code files} là ảnh. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductResponse> create(
            @Valid @RequestPart("data") ProductRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ApiResponse.ok(productService.create(request, files));
    }

    /** Tạo sản phẩm bằng JSON (app gửi thông tin trước, ảnh tải sau qua {@code /{id}/images}). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ProductResponse> createJson(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(productService.create(request, null));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> getById(@PathVariable Long id) {
        ProductDetailResponse detail = productService.getById(id);
        if (viewCounter != null) {
            viewCounter.record(id, detail.getStoreId());
        }
        return ApiResponse.ok(detail);
    }

    /** Sản phẩm của cửa hàng người gọi, gồm cả sản phẩm đang ẩn. */
    @GetMapping("/mine")
    public ApiResponse<List<ProductResponse>> getMine() {
        return ApiResponse.ok(productService.getMine());
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> getAll(
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long storeId) {
        if (storeId != null) {
            return ApiResponse.ok(productService.getAllByStore(storeId));
        }
        if (categoryId != null) {
            return ApiResponse.ok(productService.getAllByCategory(categoryId));
        }
        return ApiResponse.ok(productService.getAll());
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<String>> uploadImages(
            @PathVariable Long id,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ApiResponse.ok(productService.uploadImages(id, files));
    }

    /** Sản phẩm tương tự (công khai) cho trang chi tiết. */
    /** "Khách mua sản phẩm này cũng mua" — theo các đơn có chung sản phẩm (không tính đơn huỷ). */
    @GetMapping("/{id:\\d+}/bought-together")
    public ApiResponse<List<ProductResponse>> boughtTogether(@PathVariable Long id,
                                                             @RequestParam(defaultValue = "8") int size) {
        return ApiResponse.ok(productService.boughtTogether(id, size));
    }

    @GetMapping("/{id:\\d+}/related")
    public ApiResponse<List<ProductResponse>> related(@PathVariable Long id, @RequestParam(defaultValue = "8") int size) {
        return ApiResponse.ok(productService.related(id, size));
    }

    /** Bộ ảnh của sản phẩm kèm id (để gỡ / chọn ảnh đại diện); người của cửa hàng và ADMIN. */
    @GetMapping("/{id}/images")
    public ApiResponse<List<ProductImageResponse>> images(@PathVariable Long id) {
        return ApiResponse.ok(productService.images(id));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ApiResponse<List<ProductImageResponse>> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        return ApiResponse.ok(productService.deleteImage(id, imageId));
    }

    @PutMapping("/{id}/images/{imageId}/cover")
    public ApiResponse<List<ProductImageResponse>> setCover(@PathVariable Long id, @PathVariable Long imageId) {
        return ApiResponse.ok(productService.setCover(id, imageId));
    }

    @PostMapping("/search")
    public ApiResponse<PageResponse<ProductResponse>> search(@RequestBody ProductSearchRequest request) {
        return ApiResponse.ok(productService.search(request));
    }
}
