package com.example.elsa_store.dto.response;

import java.util.List;

/**
 * Kết quả nhập sản phẩm từ Excel. Có lỗi ở bất kỳ dòng nào thì không tạo gì ({@code applied} = false) và {@code errors}
 * liệt kê từng dòng (số dòng như trong Excel, dòng tiêu đề là 1).
 *
 * @param applied đã ghi vào cửa hàng chưa (false khi có lỗi hoặc chỉ xem trước)
 * @param rows số dòng dữ liệu đọc được
 * @param productsCreated sản phẩm mới
 * @param productsExtended sản phẩm đã có được thêm phân loại
 * @param variantsCreated phân loại mới
 * @param stockTracked phân loại bắt đầu theo dõi kho (có cột tồn kho)
 */
public record ProductImportResponse(
        boolean applied,
        int rows,
        int productsCreated,
        int productsExtended,
        int variantsCreated,
        int stockTracked,
        List<Issue> errors) {

    public record Issue(int row, String message) {}
}
