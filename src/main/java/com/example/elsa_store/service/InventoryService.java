package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.InventoryRequest;
import com.example.elsa_store.dto.request.StockAdjustRequest;
import com.example.elsa_store.dto.response.InventoryResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StockItemResponse;
import com.example.elsa_store.dto.response.StockMovementResponse;
import com.example.elsa_store.dto.response.StockOverviewResponse;

import java.util.List;

public interface InventoryService {
    InventoryResponse create(InventoryRequest req);
    InventoryResponse update(Long id, InventoryRequest req);
    void delete(Long id);
    InventoryResponse getById(Long id);
    List<InventoryResponse> getAll();

    // ------------------------------------------------------------------ màn kho hàng (chủ / quản lý)

    /** {@code filter}: ALL, TRACKED, UNTRACKED, LOW, OUT. */
    PageResponse<StockItemResponse> stock(String keyword, String filter, int page, int size);

    StockOverviewResponse overview();

    /** Kiểm kê số lượng, đổi mức cảnh báo, đổi giá vốn — trường để trống giữ nguyên. */
    StockItemResponse adjust(Long variantId, StockAdjustRequest req);

    /** Ngừng theo dõi kho: biến thể bán không giới hạn. */
    StockItemResponse stopTracking(Long variantId);

    /** Thẻ kho của biến thể, mới nhất trước. */
    PageResponse<StockMovementResponse> movements(Long variantId, int page, int size);
}
