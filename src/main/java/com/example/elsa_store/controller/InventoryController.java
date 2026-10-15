
package com.example.elsa_store.controller;

import com.example.elsa_store.dto.request.InventoryRequest;
import com.example.elsa_store.dto.request.StockAdjustRequest;
import com.example.elsa_store.dto.response.InventoryResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.StockItemResponse;
import com.example.elsa_store.dto.response.StockMovementResponse;
import com.example.elsa_store.dto.response.StockOverviewResponse;
import com.example.elsa_store.service.InventoryService;
import jakarta.validation.Valid;
import com.example.elsa_store.dto.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventories")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public ApiResponse<InventoryResponse> create(@Valid @RequestBody InventoryRequest req) {
        return ApiResponse.ok(inventoryService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<InventoryResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody InventoryRequest req) {
        return ApiResponse.ok(inventoryService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        inventoryService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<InventoryResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(inventoryService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<InventoryResponse>> getAll() {
        return ApiResponse.ok(inventoryService.getAll());
    }

    // ------------------------------------------------------------------ màn kho hàng (chủ / quản lý)

    /** Danh sách kho: mọi biến thể của cửa hàng kèm tồn kho. {@code filter}: ALL, TRACKED, UNTRACKED, LOW, OUT. */
    @GetMapping("/stock")
    public ApiResponse<PageResponse<StockItemResponse>> stock(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inventoryService.stock(keyword, filter, page, size));
    }

    @GetMapping("/overview")
    public ApiResponse<StockOverviewResponse> overview() {
        return ApiResponse.ok(inventoryService.overview());
    }

    @PutMapping("/variants/{variantId}")
    public ApiResponse<StockItemResponse> adjust(@PathVariable Long variantId,
                                                 @Valid @RequestBody StockAdjustRequest req) {
        return ApiResponse.ok(inventoryService.adjust(variantId, req));
    }

    @DeleteMapping("/variants/{variantId}")
    public ApiResponse<StockItemResponse> stopTracking(@PathVariable Long variantId) {
        return ApiResponse.ok(inventoryService.stopTracking(variantId));
    }

    @GetMapping("/variants/{variantId}/movements")
    public ApiResponse<PageResponse<StockMovementResponse>> movements(
            @PathVariable Long variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inventoryService.movements(variantId, page, size));
    }
}
