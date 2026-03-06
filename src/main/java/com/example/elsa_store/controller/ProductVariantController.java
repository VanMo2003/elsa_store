
package com.example.elsa_store.controller;

import com.example.elsa_store.dto.request.ProductVariantRequest;
import com.example.elsa_store.dto.response.ProductVariantDetailResponse;
import com.example.elsa_store.dto.response.ProductVariantResponse;
import com.example.elsa_store.service.ProductVariantService;
import jakarta.validation.Valid;
import com.example.elsa_store.dto.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/product-variants")
public class ProductVariantController {

    private final ProductVariantService productVariantService;

    public ProductVariantController(ProductVariantService productVariantService) {
        this.productVariantService = productVariantService;
    }

    @PostMapping
    public ApiResponse<ProductVariantResponse> create(
            @Valid @RequestPart("data") ProductVariantRequest req,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ApiResponse.ok(productVariantService.create(req, files));

    }

    @PutMapping("/{id}")
    public ApiResponse<ProductVariantResponse> update(@PathVariable Long id,
                                                         @Valid @RequestBody ProductVariantRequest req) {
        return ApiResponse.ok(productVariantService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productVariantService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductVariantDetailResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(productVariantService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<ProductVariantResponse>> getAll(@RequestParam("productId") Long productId) {
        return ApiResponse.ok(productVariantService.getAll(productId));
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<String>> uploadImages(
            @PathVariable Long id,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ApiResponse.ok(productVariantService.uploadImages(id, files));
    }
}
