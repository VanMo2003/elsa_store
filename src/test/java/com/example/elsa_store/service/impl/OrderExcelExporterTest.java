package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.entity.Customer;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.User;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderExcelExporterTest {

    private static Order order(String code, OrderStatus status, double goods, double discount, Double shipping) {
        ProductVariant variant = ProductVariant.builder()
                .product(Product.builder().name("Giày Samba").build())
                .color("Trắng")
                .size("42")
                .sku("SB-42")
                .build();
        OrderItem item = OrderItem.builder()
                .productVariant(variant)
                .quantity(2)
                .originalUnitPrice(goods / 2)
                .unitPrice((goods - discount) / 2)
                .lineTotal(goods - discount)
                .build();
        Order order = Order.builder()
                .code(code)
                .orderDate(LocalDateTime.of(2026, 10, 15, 9, 30))
                .status(status)
                .paymentMethod("COD")
                .paymentStatus(PaymentStatus.CHUA_THANH_TOAN)
                .totalAmount(goods)
                .discountAmount(discount)
                .shippingFee(shipping)
                .finalAmount(goods - discount + (shipping == null ? 0 : shipping))
                .receiverName("Nguyễn Văn A")
                .receiverPhone("0901234567")
                .shippingAddress("12 Lê Lợi, Q1")
                .customer(Customer.builder().fullName("Khách A").phone("0901234567").build())
                .items(List.of(item))
                .build();
        if (status == OrderStatus.DA_HUY) {
            order.setCancelReason("Đặt nhầm size");
        }
        return order;
    }

    @Test
    void ordersSheetHasOneRowPerOrderAndTotalsSkipCancelledOrders() throws Exception {
        User staff = User.builder().username("nv01").fullName("Trần Nhân Viên").build();
        Order counter = order("ORD-2", OrderStatus.HOAN_THANH, 500_000, 0, null);
        counter.setHandledBy(staff);
        byte[] file = new OrderExcelExporter().export(List.of(
                order("ORD-1", OrderStatus.DANG_GIAO, 1_000_000, 100_000, 30_000.0),
                counter,
                order("ORD-3", OrderStatus.DA_HUY, 200_000, 0, 30_000.0)));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet orders = workbook.getSheet("Đơn hàng");
            assertEquals("Mã đơn", orders.getRow(0).getCell(0).getStringCellValue());
            assertEquals("ORD-1", orders.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Đang giao", orders.getRow(1).getCell(2).getStringCellValue());
            assertEquals("Online", orders.getRow(1).getCell(5).getStringCellValue());
            assertEquals(930_000, orders.getRow(1).getCell(15).getNumericCellValue());
            assertEquals("Tại quầy", orders.getRow(2).getCell(5).getStringCellValue());
            assertEquals("Trần Nhân Viên", orders.getRow(2).getCell(17).getStringCellValue());
            assertNull(orders.getRow(2).getCell(14), "đơn tại quầy không có phí giao");
            assertEquals("Đặt nhầm size", orders.getRow(3).getCell(19).getStringCellValue());

            // Dòng tổng cách một dòng trống, không cộng đơn đã huỷ.
            var total = orders.getRow(5);
            assertEquals("Tổng (không tính đơn đã huỷ)", total.getCell(0).getStringCellValue());
            assertEquals(1_500_000, total.getCell(12).getNumericCellValue());
            assertEquals(30_000, total.getCell(14).getNumericCellValue());
            assertEquals(1_430_000, total.getCell(15).getNumericCellValue());

            Sheet items = workbook.getSheet("Chi tiết");
            assertEquals(3, items.getLastRowNum(), "mỗi đơn một món");
            assertEquals("Giày Samba", items.getRow(1).getCell(2).getStringCellValue());
            assertEquals("SB-42", items.getRow(1).getCell(5).getStringCellValue());
            assertEquals(2, items.getRow(1).getCell(6).getNumericCellValue());
            assertEquals(900_000, items.getRow(1).getCell(9).getNumericCellValue());
        }
    }
}
