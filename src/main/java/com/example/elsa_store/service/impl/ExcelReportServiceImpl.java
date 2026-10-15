package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.*;
import com.example.elsa_store.service.ExcelReportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Xuất báo cáo ra Excel bằng Apache POI: mỗi báo cáo một sheet, số tiền là ô số để mở ra tính tiếp được. */
@Service
public class ExcelReportServiceImpl implements ExcelReportService {

    @Override
    public byte[] export(
            RevenueSummaryResponse summary,
            RevenueTimeSeriesResponse revenue,
            List<RevenueByCategoryResponse> byCategory,
            List<ProductRevenueResponse> topProducts,
            List<TopCustomerResponse> topCustomers,
            List<StaffPerformanceResponse> staff,
            CancellationRateResponse cancellation,
            List<LowStockResponse> lowStock,
            RevenueBreakdownResponse breakdown,
            StockOverviewResponse stockOverview) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);

            Sheet overview = workbook.createSheet("Tổng quan");
            int r = 0;
            r = title(overview, styles, r, "Báo cáo doanh thu " + summary.getFrom() + " → " + summary.getTo());
            r = pair(overview, styles, r, "Số đơn", summary.getOrdersCount(), false);
            r = pair(overview, styles, r, "Doanh thu gộp", summary.getGrossRevenue(), true);
            r = pair(overview, styles, r, "Giảm giá", summary.getDiscountTotal(), true);
            if (summary.getShippingTotal() != null && summary.getShippingTotal() > 0) {
                r = pair(overview, styles, r, "Phí giao hàng khách trả", summary.getShippingTotal(), true);
            }
            r = pair(overview, styles, r, "Doanh thu thuần", summary.getNetRevenue(), true);
            if (summary.getRefundTotal() != null && summary.getRefundTotal() > 0) {
                r = pair(overview, styles, r, "Hoàn tiền trả hàng (" + summary.getRefundCount() + " yêu cầu)",
                        summary.getRefundTotal(), true);
                r = pair(overview, styles, r, "Doanh thu sau hoàn tiền", summary.getNetAfterRefunds(), true);
            }
            r = pair(overview, styles, r, "Giá trị đơn trung bình", summary.getAverageOrderValue(), true);
            r = pair(overview, styles, r, "Giá vốn hàng bán", summary.getCostOfGoods(), true);
            r = pair(overview, styles, r, "Lợi nhuận gộp", summary.getGrossProfit(), true);
            r = ratio(overview, styles, r, "Biên lợi nhuận gộp", summary.getProfitMargin());
            if (summary.getUncostedQuantity() != null && summary.getUncostedQuantity() > 0) {
                r = pair(overview, styles, r, "Số lượng bán chưa có giá vốn (lợi nhuận có thể cao hơn thực tế)",
                        summary.getUncostedQuantity(), false);
            }
            RevenueSummaryResponse previous = summary.getPrevious();
            if (previous != null) {
                r++;
                r = title(overview, styles, r, "So với kỳ trước " + previous.getFrom() + " → " + previous.getTo()) - 1;
                r = pair(overview, styles, r, "Doanh thu thuần kỳ trước", previous.getNetRevenue(), true);
                r = ratio(overview, styles, r, "Tăng trưởng doanh thu", summary.getRevenueGrowth());
                r = pair(overview, styles, r, "Số đơn kỳ trước", previous.getOrdersCount(), false);
                r = ratio(overview, styles, r, "Tăng trưởng số đơn", summary.getOrdersGrowth());
                r = pair(overview, styles, r, "Lợi nhuận gộp kỳ trước", previous.getGrossProfit(), true);
                r = ratio(overview, styles, r, "Tăng trưởng lợi nhuận", summary.getProfitGrowth());
                r++;
            }
            r = pair(overview, styles, r, "Tổng số đơn trong kỳ (mọi trạng thái)", cancellation.getTotalOrders(), false);
            r = pair(overview, styles, r, "Số đơn đã huỷ", cancellation.getCancelledOrders(), false);
            Row rateRow = overview.createRow(r);
            rateRow.createCell(0).setCellValue("Tỉ lệ huỷ");
            Cell rateCell = rateRow.createCell(1);
            rateCell.setCellValue(cancellation.getRate() == null ? 0 : cancellation.getRate());
            rateCell.setCellStyle(styles.percent);
            overview.autoSizeColumn(0);
            overview.setColumnWidth(1, 5000);

            Sheet byPeriod = workbook.createSheet("Doanh thu");
            header(byPeriod, styles, "Kỳ", "Số đơn", "Doanh thu");
            int row = 1;
            for (RevenuePointResponse p : revenue.getPoints()) {
                Row x = byPeriod.createRow(row++);
                x.createCell(0).setCellValue(p.getPeriod());
                x.createCell(1).setCellValue(p.getOrdersCount());
                money(x, 2, p.getNetRevenue(), styles);
            }
            widths(byPeriod, 3);

            Sheet categories = workbook.createSheet("Theo danh mục");
            header(categories, styles, "Danh mục", "Số lượng bán", "Doanh thu");
            row = 1;
            for (RevenueByCategoryResponse c : byCategory) {
                Row x = categories.createRow(row++);
                x.createCell(0).setCellValue(c.getCategoryName());
                x.createCell(1).setCellValue(c.getQuantitySold());
                money(x, 2, c.getRevenue(), styles);
            }
            widths(categories, 3);

            Sheet products = workbook.createSheet("Sản phẩm bán chạy");
            header(products, styles, "Sản phẩm", "Số lượng bán", "Doanh thu", "Giá vốn", "Lợi nhuận gộp");
            row = 1;
            for (ProductRevenueResponse p : topProducts) {
                Row x = products.createRow(row++);
                x.createCell(0).setCellValue(p.getProductName());
                x.createCell(1).setCellValue(p.getQuantitySold());
                money(x, 2, p.getRevenue(), styles);
                money(x, 3, p.getCostOfGoods(), styles);
                if (p.getProfit() == null) {
                    x.createCell(4).setCellValue("Chưa đủ giá vốn");
                } else {
                    money(x, 4, p.getProfit(), styles);
                }
            }
            widths(products, 5);

            if (breakdown != null) {
                Sheet channels = workbook.createSheet("Kênh bán");
                header(channels, styles, "Nhóm", "Số đơn", "Doanh thu", "Tỷ trọng");
                row = 1;
                for (RevenueBreakdownResponse.Slice slice : breakdown.getByPaymentMethod()) {
                    row = slice(channels, styles, row, "Thanh toán: " + label(slice.getKey()), slice);
                }
                for (RevenueBreakdownResponse.Slice slice : breakdown.getByChannel()) {
                    row = slice(channels, styles, row, "Kênh: " + label(slice.getKey()), slice);
                }
                widths(channels, 4);
            }

            Sheet customers = workbook.createSheet("Khách hàng");
            header(customers, styles, "Khách hàng", "Điện thoại", "Số đơn", "Tổng chi tiêu");
            row = 1;
            for (TopCustomerResponse c : topCustomers) {
                Row x = customers.createRow(row++);
                x.createCell(0).setCellValue(c.getName());
                x.createCell(1).setCellValue(c.getPhone() == null ? "" : c.getPhone());
                x.createCell(2).setCellValue(c.getOrdersCount());
                money(x, 3, c.getTotalSpent(), styles);
            }
            widths(customers, 4);

            Sheet staffSheet = workbook.createSheet("Nhân viên");
            header(staffSheet, styles, "Người lập đơn", "Vai trò", "Số đơn", "Doanh thu");
            row = 1;
            for (StaffPerformanceResponse s : staff) {
                Row x = staffSheet.createRow(row++);
                x.createCell(0).setCellValue(s.getName());
                x.createCell(1).setCellValue(s.getRole().name());
                x.createCell(2).setCellValue(s.getOrdersCount());
                money(x, 3, s.getRevenue(), styles);
            }
            widths(staffSheet, 4);

            Sheet stock = workbook.createSheet("Tồn kho thấp");
            header(stock, styles, "Sản phẩm", "Màu", "Size", "SKU", "Tồn kho", "Mức tối thiểu");
            row = 1;
            for (LowStockResponse s : lowStock) {
                Row x = stock.createRow(row++);
                x.createCell(0).setCellValue(s.getProductName());
                x.createCell(1).setCellValue(s.getColor() == null ? "" : s.getColor());
                x.createCell(2).setCellValue(s.getSize() == null ? "" : s.getSize());
                x.createCell(3).setCellValue(s.getSku() == null ? "" : s.getSku());
                x.createCell(4).setCellValue(s.getQuantity() == null ? 0 : s.getQuantity());
                x.createCell(5).setCellValue(s.getMinQuantity() == null ? 0 : s.getMinQuantity());
            }
            widths(stock, 6);

            if (stockOverview != null) {
                Sheet value = workbook.createSheet("Giá trị kho");
                int v = title(value, styles, 0, "Tồn kho hiện tại");
                v = pair(value, styles, v, "Số biến thể", stockOverview.getTotalVariants(), false);
                v = pair(value, styles, v, "Chưa theo dõi kho", stockOverview.getUntrackedVariants(), false);
                v = pair(value, styles, v, "Sắp hết hàng", stockOverview.getLowStockVariants(), false);
                v = pair(value, styles, v, "Hết hàng", stockOverview.getOutOfStockVariants(), false);
                v = pair(value, styles, v, "Tổng số lượng tồn", stockOverview.getTotalUnits(), false);
                v = pair(value, styles, v, "Giá trị tồn theo giá vốn", stockOverview.getStockValue(), true);
                v = pair(value, styles, v, "Giá trị tồn theo giá bán", stockOverview.getRetailValue(), true);
                pair(value, styles, v, "Biến thể còn hàng chưa có giá vốn", stockOverview.getUncostedVariants(), false);
                value.autoSizeColumn(0);
                value.setColumnWidth(1, 5000);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build the Excel report", e);
        }
    }

    private static int title(Sheet sheet, Styles styles, int rowIndex, String text) {
        Cell cell = sheet.createRow(rowIndex).createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(styles.title);
        return rowIndex + 2;
    }

    private static int pair(Sheet sheet, Styles styles, int rowIndex, String label, Number value, boolean money) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        Cell cell = row.createCell(1);
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        if (money) {
            cell.setCellStyle(styles.money);
        }
        return rowIndex + 1;
    }

    /** Ô phần trăm từ số dạng 12.5 (= 12,5%); trống khi không có số liệu để so. */
    private static int ratio(Sheet sheet, Styles styles, int rowIndex, String label, Double percent) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        Cell cell = row.createCell(1);
        if (percent == null) {
            cell.setCellValue("—");
        } else {
            cell.setCellValue(percent / 100);
            cell.setCellStyle(styles.percent);
        }
        return rowIndex + 1;
    }

    private static int slice(Sheet sheet, Styles styles, int rowIndex, String label, RevenueBreakdownResponse.Slice s) {
        Row x = sheet.createRow(rowIndex);
        x.createCell(0).setCellValue(label);
        x.createCell(1).setCellValue(s.getOrdersCount() == null ? 0 : s.getOrdersCount());
        money(x, 2, s.getRevenue(), styles);
        Cell share = x.createCell(3);
        share.setCellValue(s.getShare() == null ? 0 : s.getShare() / 100);
        share.setCellStyle(styles.percent);
        return rowIndex + 1;
    }

    static String label(String key) {
        return switch (key == null ? "" : key) {
            case "COD" -> "Tiền mặt / COD";
            case "VNPAY" -> "VNPay";
            case "ONLINE" -> "Khách đặt online";
            case "COUNTER" -> "Bán tại quầy";
            default -> "Khác";
        };
    }

    private static void header(Sheet sheet, Styles styles, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(styles.header);
        }
    }

    private static void money(Row row, int column, Double value, Styles styles) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0 : value);
        cell.setCellStyle(styles.money);
    }

    private static void widths(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle money;
        final CellStyle percent;

        Styles(Workbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            title = workbook.createCellStyle();
            title.setFont(bold);
            header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            // BE trả tỷ lệ dạng phân số (0.333…); Excel tự nhân 100 khi định dạng phần trăm.
            percent = workbook.createCellStyle();
            percent.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));
        }
    }
}
