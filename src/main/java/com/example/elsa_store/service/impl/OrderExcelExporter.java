package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.entity.Customer;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.User;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/** Tệp Excel danh sách đơn: sheet "Đơn hàng" (một dòng một đơn, dòng tổng cuối) và sheet "Chi tiết" (từng món). */
@Component
public class OrderExcelExporter {

    private static final String[] ORDER_COLUMNS = {
        "Mã đơn", "Ngày đặt", "Trạng thái", "Cách trả", "Thanh toán", "Kênh bán", "Khách hàng", "SĐT khách",
        "Người nhận", "SĐT nhận", "Địa chỉ giao", "Số sản phẩm", "Tiền hàng", "Giảm giá", "Phí giao", "Khách trả",
        "Mã giảm giá", "Lập bởi", "Hoàn thành lúc", "Lý do huỷ", "Vận chuyển", "Mã vận đơn"
    };
    private static final String[] ITEM_COLUMNS = {
        "Mã đơn", "Ngày đặt", "Sản phẩm", "Màu", "Size", "SKU", "Số lượng", "Đơn giá gốc", "Đơn giá bán", "Thành tiền"
    };

    public byte[] export(List<Order> orders) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet("Đơn hàng");
            header(sheet, styles, ORDER_COLUMNS);
            double goods = 0;
            double discount = 0;
            double shipping = 0;
            double paid = 0;
            int r = 1;
            for (Order o : orders) {
                Row row = sheet.createRow(r++);
                int c = 0;
                text(row, c++, o.getCode());
                date(row, c++, o.getOrderDate(), styles);
                text(row, c++, statusLabel(o.getStatus()));
                text(row, c++, "VNPAY".equals(o.getPaymentMethod()) ? "VNPay" : "COD");
                text(row, c++, paymentLabel(o.getPaymentStatus()));
                text(row, c++, o.getHandledBy() != null ? "Tại quầy" : "Online");
                text(row, c++, customerName(o));
                text(row, c++, customerPhone(o));
                text(row, c++, o.getReceiverName());
                text(row, c++, o.getReceiverPhone());
                text(row, c++, o.getShippingAddress());
                row.createCell(c++).setCellValue(quantity(o));
                money(row, c++, o.getTotalAmount(), styles);
                money(row, c++, o.getDiscountAmount(), styles);
                money(row, c++, o.getShippingFee(), styles);
                money(row, c++, o.getFinalAmount(), styles);
                text(row, c++, o.getCouponCode());
                text(row, c++, name(o.getHandledBy()));
                date(row, c++, o.getCompletedAt(), styles);
                text(row, c++, o.getCancelReason());
                text(row, c++, o.getShippingCarrier());
                text(row, c, o.getTrackingCode());
                if (o.getStatus() != OrderStatus.DA_HUY) {
                    goods += nz(o.getTotalAmount());
                    discount += nz(o.getDiscountAmount());
                    shipping += nz(o.getShippingFee());
                    paid += nz(o.getFinalAmount());
                }
            }
            Row total = sheet.createRow(r + 1);
            Cell label = total.createCell(0);
            label.setCellValue("Tổng (không tính đơn đã huỷ)");
            label.setCellStyle(styles.bold);
            money(total, 12, goods, styles);
            money(total, 13, discount, styles);
            money(total, 14, shipping, styles);
            money(total, 15, paid, styles);
            sheet.createFreezePane(1, 1);
            widths(sheet, ORDER_COLUMNS.length);

            Sheet items = workbook.createSheet("Chi tiết");
            header(items, styles, ITEM_COLUMNS);
            int i = 1;
            for (Order o : orders) {
                if (o.getItems() == null) {
                    continue;
                }
                for (OrderItem item : o.getItems()) {
                    ProductVariant v = item.getProductVariant();
                    Row row = items.createRow(i++);
                    text(row, 0, o.getCode());
                    date(row, 1, o.getOrderDate(), styles);
                    text(row, 2, v.getProduct() == null ? null : v.getProduct().getName());
                    text(row, 3, v.getColor());
                    text(row, 4, v.getSize());
                    text(row, 5, v.getSku());
                    row.createCell(6).setCellValue(item.getQuantity() == null ? 0 : item.getQuantity());
                    money(row, 7, item.getOriginalUnitPrice() != null ? item.getOriginalUnitPrice() : item.getUnitPrice(),
                            styles);
                    money(row, 8, item.getUnitPrice(), styles);
                    money(row, 9, item.getLineTotal(), styles);
                }
            }
            items.createFreezePane(1, 1);
            widths(items, ITEM_COLUMNS.length);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build the order Excel file", e);
        }
    }

    static String statusLabel(OrderStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case CHUA_XAC_NHAN -> "Chờ xác nhận";
            case DA_XAC_NHAN -> "Đã xác nhận";
            case DANG_GIAO -> "Đang giao";
            case HOAN_THANH -> "Hoàn thành";
            case DA_HUY -> "Đã huỷ";
        };
    }

    static String paymentLabel(PaymentStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case CHUA_THANH_TOAN -> "Chưa thanh toán";
            case CHO_THANH_TOAN -> "Chờ thanh toán";
            case DA_THANH_TOAN -> "Đã thanh toán";
            case THAT_BAI -> "Thanh toán lỗi";
        };
    }

    private static int quantity(Order o) {
        return o.getItems() == null
                ? 0
                : o.getItems().stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
    }

    private static String customerName(Order o) {
        Customer c = o.getCustomer();
        if (c == null) {
            return o.getHandledBy() != null ? "Khách lẻ" : name(o.getUser());
        }
        return c.getFullName() != null ? c.getFullName() : name(c.getUser());
    }

    private static String customerPhone(Order o) {
        Customer c = o.getCustomer();
        if (c != null && c.getPhone() != null) {
            return c.getPhone();
        }
        User user = c != null ? c.getUser() : o.getUser();
        return user == null ? null : user.getPhone();
    }

    private static String name(User user) {
        if (user == null) {
            return null;
        }
        return user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName();
    }

    private static double nz(Double value) {
        return value == null ? 0 : value;
    }

    private static void text(Row row, int column, String value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    private static void money(Row row, int column, Double value, Styles styles) {
        if (value == null) {
            return;
        }
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(styles.money);
    }

    private static void date(Row row, int column, LocalDateTime value, Styles styles) {
        if (value == null) {
            return;
        }
        Cell cell = row.createCell(column);
        cell.setCellValue(Date.from(value.atZone(ZoneId.systemDefault()).toInstant()));
        cell.setCellStyle(styles.date);
    }

    private static void header(Sheet sheet, Styles styles, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(styles.header);
        }
    }

    private static void widths(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            // Địa chỉ dài thì giới hạn độ rộng cho dễ xem.
            if (sheet.getColumnWidth(i) > 12000) {
                sheet.setColumnWidth(i, 12000);
            }
        }
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle bold;
        final CellStyle money;
        final CellStyle date;

        Styles(Workbook workbook) {
            Font font = workbook.createFont();
            font.setBold(true);
            header = workbook.createCellStyle();
            header.setFont(font);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bold = workbook.createCellStyle();
            bold.setFont(font);
            money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy HH:mm"));
        }
    }
}
