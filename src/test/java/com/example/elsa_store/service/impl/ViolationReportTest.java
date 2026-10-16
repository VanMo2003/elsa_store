package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.constant.ViolationReason;
import com.example.elsa_store.constant.ViolationStatus;
import com.example.elsa_store.dto.request.ViolationHandleRequest;
import com.example.elsa_store.dto.request.ViolationReportRequest;
import com.example.elsa_store.dto.response.ViolationReportResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.entity.ViolationReport;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ViolationReportRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Báo cáo vi phạm: chỉ khách, chỉ sản phẩm đang bán, không trùng khi đang chờ; ADMIN xử lý thì báo người báo cáo một lần. */
class ViolationReportTest {

    private final ViolationReportRepository reports = mock(ViolationReportRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ViolationReportServiceImpl service =
            new ViolationReportServiceImpl(reports, products, accessGuard, notifications);

    private User fan;
    private Product product;

    @BeforeEach
    void setUp() {
        fan = User.builder().username("khach001").role(Role.USER).build();
        fan.setId(7L);
        Store store = Store.builder().name("Elsa Q1").approvalStatus(ApprovalStatus.APPROVED).build();
        store.setId(3L);
        product = Product.builder().name("Giày nhái").status(1).store(store).build();
        product.setId(10L);
        when(accessGuard.currentUser()).thenReturn(fan);
        when(products.findById(10L)).thenReturn(Optional.of(product));
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void customersReportAndAdminsHearAboutIt() {
        ViolationReportResponse res = service.report(10L, new ViolationReportRequest(ViolationReason.FAKE, "  Logo sai  "));

        assertEquals("Logo sai", res.note());
        assertEquals(ViolationStatus.PENDING, res.status());
        assertEquals("Elsa Q1", res.storeName());
        assertEquals("k******1", res.reporterName());
        verify(notifications).notifyAdmins(eq(NotificationType.PRODUCT_REPORTED),
                argThat(m -> m.title().contains("hàng giả") && m.body().contains("Giày nhái")),
                eq("elsastore://violations"), any());
    }

    @Test
    void duplicatesStaffAndHiddenProductsAreRejected() {
        when(reports.existsByUser_IdAndProduct_IdAndStatus(7L, 10L, ViolationStatus.PENDING)).thenReturn(true);
        AppException dup = assertThrows(AppException.class,
                () -> service.report(10L, new ViolationReportRequest(ViolationReason.SCAM, null)));
        assertEquals(ErrorCode.DATA_IN_USE, dup.getErrorCode());

        product.setStatus(0);
        assertThrows(ResourceNotFoundException.class,
                () -> service.report(10L, new ViolationReportRequest(ViolationReason.SCAM, null)));

        when(accessGuard.currentUser()).thenReturn(User.builder().username("nv").role(Role.STAFF).build());
        assertThrows(AppException.class, () -> service.report(10L, new ViolationReportRequest(ViolationReason.SCAM, null)));
        verify(reports, never()).save(any());
    }

    @Test
    void handlingNotifiesTheReporterOnce() {
        User admin = User.builder().username("admin").role(Role.ADMIN).build();
        when(accessGuard.requireAdmin()).thenReturn(admin);
        ViolationReport report = ViolationReport.builder().product(product).store(product.getStore()).user(fan)
                .reason(ViolationReason.FAKE).build();
        report.setId(5L);
        when(reports.findById(5L)).thenReturn(Optional.of(report));

        ViolationReportResponse res = service.handle(5L, new ViolationHandleRequest(ViolationStatus.RESOLVED, "Đã ẩn sản phẩm"));
        assertEquals(ViolationStatus.RESOLVED, res.status());
        assertSame(admin, report.getHandledBy());
        service.handle(5L, new ViolationHandleRequest(ViolationStatus.DISMISSED, null));

        verify(notifications, times(1)).notify(eq(fan), eq(NotificationType.REPORT_HANDLED),
                argThat(m -> m.body().contains("Đã ẩn sản phẩm")), any(), eq(5L));
        assertThrows(AppException.class, () -> service.handle(5L, new ViolationHandleRequest(ViolationStatus.PENDING, null)));
    }
}
