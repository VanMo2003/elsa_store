package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.constant.ViolationStatus;
import com.example.elsa_store.dto.request.ViolationHandleRequest;
import com.example.elsa_store.dto.request.ViolationReportRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.ViolationReportResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.entity.ViolationReport;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ViolationReportRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.service.ViolationReportService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class ViolationReportServiceImpl implements ViolationReportService {

    private final ViolationReportRepository reportRepository;
    private final ProductRepository productRepository;
    private final AccessGuard accessGuard;
    private final NotificationService notificationService;

    public ViolationReportServiceImpl(ViolationReportRepository reportRepository,
                                      ProductRepository productRepository,
                                      AccessGuard accessGuard,
                                      NotificationService notificationService) {
        this.reportRepository = reportRepository;
        this.productRepository = productRepository;
        this.accessGuard = accessGuard;
        this.notificationService = notificationService;
    }

    @Override
    public ViolationReportResponse report(Long productId, ViolationReportRequest request) {
        User user = accessGuard.currentUser();
        if (user.getRole() != Role.USER) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Only customers can report products");
        }
        Product product = productRepository.findById(productId)
                .filter(ProductServiceImpl::isPublic)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (reportRepository.existsByUser_IdAndProduct_IdAndStatus(user.getId(), productId, ViolationStatus.PENDING)) {
            throw new AppException(ErrorCode.DATA_IN_USE, "You already reported this product, it is being reviewed");
        }
        ViolationReport saved = reportRepository.save(ViolationReport.builder()
                .product(product)
                .store(product.getStore())
                .user(user)
                .reason(request.getReason())
                .note(trimToNull(request.getNote()))
                .build());
        notificationService.notifyAdmins(NotificationType.PRODUCT_REPORTED,
                NotificationMessages.productReported(saved), AppLinks.violations(), saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ViolationReportResponse> list(ViolationStatus status, int page, int size) {
        accessGuard.requireAdmin();
        var result = reportRepository.findForAdmin(status,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Override
    public ViolationReportResponse handle(Long id, ViolationHandleRequest request) {
        User admin = accessGuard.requireAdmin();
        if (request.getStatus() == ViolationStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "status: must be RESOLVED or DISMISSED");
        }
        ViolationReport report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        boolean firstTime = report.getStatus() == ViolationStatus.PENDING;
        report.setStatus(request.getStatus());
        report.setAdminNote(trimToNull(request.getNote()));
        report.setHandledBy(admin);
        report.setHandledAt(LocalDateTime.now());
        ViolationReport saved = reportRepository.save(report);
        if (firstTime) {
            notificationService.notify(saved.getUser(), NotificationType.REPORT_HANDLED,
                    NotificationMessages.reportHandled(saved), AppLinks.notifications(), saved.getId());
        }
        return toResponse(saved);
    }

    private ViolationReportResponse toResponse(ViolationReport v) {
        Product p = v.getProduct();
        return new ViolationReportResponse(
                v.getId(),
                p.getId(),
                p.getName(),
                p.getImageUrl(),
                v.getStore() == null ? null : v.getStore().getId(),
                v.getStore() == null ? null : v.getStore().getName(),
                v.getStore() != null && v.getStore().isSuspended(),
                ProductReviewServiceImpl.maskedName(v.getUser().getUsername()),
                v.getReason(),
                v.getNote(),
                v.getStatus(),
                v.getAdminNote(),
                v.getCreatedAt(),
                v.getHandledAt(),
                reportRepository.countByProduct_IdAndStatus(p.getId(), ViolationStatus.PENDING));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
