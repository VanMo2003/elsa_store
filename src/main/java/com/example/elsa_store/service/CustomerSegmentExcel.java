package com.example.elsa_store.service;

import com.example.elsa_store.constant.CustomerSegment;
import com.example.elsa_store.dto.response.CustomerSegmentsResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Tệp Excel danh sách khách theo nhóm (để gọi điện / nhắn tin chăm sóc ngoài app): mỗi dòng một khách — nhóm, tên, SĐT,
 * email, số đơn, tổng chi, lần mua cuối, số ngày từ đó. Quyền như {@link CustomerSegments} (chủ / quản lý).
 */
@Service
@RequiredArgsConstructor
public class CustomerSegmentExcel {

    static final String[] COLUMNS = {
        "Nhóm", "Khách hàng", "SĐT", "Email", "Có tài khoản", "Số đơn", "Tổng chi", "Mua lần cuối", "Số ngày chưa mua"
    };

    private final CustomerSegments customerSegments;

    public byte[] export(CustomerSegment segment) {
        return write(customerSegments.list(segment).customers());
    }

    static String label(CustomerSegment s) {
        return switch (s) {
            case VIP -> "VIP";
            case LOYAL -> "Thân thiết";
            case NEW -> "Mới";
            case AT_RISK -> "Sắp rời bỏ";
            case LOST -> "Lâu không mua";
        };
    }

    static byte[] write(List<CustomerSegmentsResponse.Item> customers) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            CellStyle date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy"));

            Sheet sheet = workbook.createSheet("Khách hàng");
            Row head = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(COLUMNS[i]);
                cell.setCellStyle(header);
            }
            int r = 1;
            for (CustomerSegmentsResponse.Item c : customers) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(label(c.segment()));
                row.createCell(1).setCellValue(c.fullName() == null ? "" : c.fullName());
                row.createCell(2).setCellValue(c.phone() == null ? "" : c.phone());
                row.createCell(3).setCellValue(c.email() == null ? "" : c.email());
                row.createCell(4).setCellValue(c.userId() != null ? "Có" : "Không");
                row.createCell(5).setCellValue(c.orders());
                Cell spent = row.createCell(6);
                spent.setCellValue(c.spent());
                spent.setCellStyle(money);
                Cell last = row.createCell(7);
                if (c.lastOrderAt() != null) {
                    last.setCellValue(Date.from(c.lastOrderAt().atZone(ZoneId.systemDefault()).toInstant()));
                    last.setCellStyle(date);
                }
                row.createCell(8).setCellValue(c.daysSinceLastOrder());
            }
            for (int i = 0; i < COLUMNS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build the customer Excel file", e);
        }
    }
}
