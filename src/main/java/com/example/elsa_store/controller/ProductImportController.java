package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.ProductImportResponse;
import com.example.elsa_store.service.impl.ProductImporter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/** Nhập sản phẩm hàng loạt từ Excel (chủ / quản lý cửa hàng). */
@RestController
@RequestMapping("/products/import")
public class ProductImportController {

    private final ProductImporter importer;

    public ProductImportController(ProductImporter importer) {
        this.importer = importer;
    }

    /** Tệp Excel mẫu: cột cần điền, ví dụ, danh mục đang có. */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("mau-nhap-san-pham.xlsx", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(importer.template());
    }

    /**
     * Nhập tệp (phần {@code file}). Có lỗi ở bất kỳ dòng nào thì không tạo gì và trả danh sách lỗi; {@code dryRun=true}
     * chỉ kiểm tra và đếm, không ghi.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductImportResponse> importFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return ApiResponse.ok(importer.importFile(file, dryRun));
    }
}
