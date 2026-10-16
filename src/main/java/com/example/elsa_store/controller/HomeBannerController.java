package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.BannerRequest;
import com.example.elsa_store.dto.response.BannerResponse;
import com.example.elsa_store.service.HomeBannerService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Banner trang chủ: {@code GET /home/banners} công khai; {@code /banners} cho ADMIN quản lý. */
@RestController
public class HomeBannerController {

    private final HomeBannerService service;

    public HomeBannerController(HomeBannerService service) {
        this.service = service;
    }

    @GetMapping("/home/banners")
    public ApiResponse<List<BannerResponse>> showing() {
        return ApiResponse.ok(service.showing());
    }

    @GetMapping("/banners")
    public ApiResponse<List<BannerResponse>> all() {
        return ApiResponse.ok(service.all());
    }

    @PostMapping("/banners")
    public ApiResponse<BannerResponse> create(@Valid @RequestBody BannerRequest body) {
        return ApiResponse.ok(service.create(body));
    }

    @PutMapping("/banners/{id}")
    public ApiResponse<BannerResponse> update(@PathVariable Long id, @Valid @RequestBody BannerRequest body) {
        return ApiResponse.ok(service.update(id, body));
    }

    /** Ảnh banner (multipart, phần {@code files}, lấy ảnh đầu tiên). */
    @PostMapping(value = "/banners/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BannerResponse> image(@PathVariable Long id, @RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.ok(service.updateImage(id, files.isEmpty() ? null : files.get(0)));
    }

    @DeleteMapping("/banners/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
