package com.example.elsa_store.service;

import com.example.elsa_store.constant.CustomerSegment;
import com.example.elsa_store.dto.response.CustomerSegmentsResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Excel danh sách khách theo nhóm. */
class CustomerSegmentExcelTest {

    @Test
    void oneRowPerCustomerWithVietnameseSegmentNames() throws Exception {
        CustomerSegments segments = mock(CustomerSegments.class);
        when(segments.list(CustomerSegment.AT_RISK)).thenReturn(new CustomerSegmentsResponse(Map.of(), List.of(
                new CustomerSegmentsResponse.Item(1L, 11L, "Chi", "0900", "chi@x.vn", 2, 1_200_000,
                        LocalDateTime.of(2026, 9, 1, 10, 0), 75, CustomerSegment.AT_RISK),
                new CustomerSegmentsResponse.Item(2L, null, null, null, null, 1, 300_000, null, 0, CustomerSegment.AT_RISK))));

        byte[] file = new CustomerSegmentExcel(segments).export(CustomerSegment.AT_RISK);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheet("Khách hàng");
            assertEquals(2, sheet.getLastRowNum());
            assertEquals("Số ngày chưa mua", sheet.getRow(0).getCell(8).getStringCellValue());
            Row chi = sheet.getRow(1);
            assertEquals("Sắp rời bỏ", chi.getCell(0).getStringCellValue());
            assertEquals("Chi", chi.getCell(1).getStringCellValue());
            assertEquals("Có", chi.getCell(4).getStringCellValue());
            assertEquals(1_200_000, chi.getCell(6).getNumericCellValue());
            assertEquals(75, chi.getCell(8).getNumericCellValue());
            assertEquals("Không", sheet.getRow(2).getCell(4).getStringCellValue());
        }
    }
}
