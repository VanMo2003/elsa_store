package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.BannerRequest;
import com.example.elsa_store.dto.response.BannerResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Banner trang chủ: ai cũng xem banner đang hiện; ADMIN thêm, sửa, xoá, đổi ảnh. */
public interface HomeBannerService {

    List<BannerResponse> showing();

    List<BannerResponse> all();

    BannerResponse create(BannerRequest request);

    BannerResponse update(Long id, BannerRequest request);

    BannerResponse updateImage(Long id, MultipartFile file);

    void delete(Long id);
}
