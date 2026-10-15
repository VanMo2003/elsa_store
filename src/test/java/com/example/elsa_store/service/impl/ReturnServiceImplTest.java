package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.ReturnStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.ReturnCreateRequest;
import com.example.elsa_store.dto.request.ReturnDecisionRequest;
import com.example.elsa_store.dto.response.ReturnResponse;
import com.example.elsa_store.entity.*;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.OrderRepository;
import com.example.elsa_store.repository.ReturnRequestRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReturnServiceImplTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 15/10/2026 12:00 giờ Việt Nam. */
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-15T05:00:00Z"), VN);

    private final ReturnRequestRepository returns = mock(ReturnRequestRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final StockService stock = mock(StockService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ReturnServiceImpl service = new ReturnServiceImpl(
            returns, orders, accessGuard, stock, notifications, new ReturnPolicy(7), NOW);

    private User buyer;
    private User staff;
    private User owner;
    private Store store;
    private Order order;
    private ProductVariant shoes;
    private ProductVariant shirt;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("Elsa").approvalStatus(ApprovalStatus.APPROVED).build();
        store.setId(1L);
        buyer = User.builder().username("khach001").fullName("Nguyễn Văn A").role(Role.USER).build();
        buyer.setId(7L);
        staff = User.builder().username("nv01").role(Role.STAFF).store(store).build();
        staff.setId(8L);
        owner = User.builder().username("chu").role(Role.STORE_OWNER).build();
        owner.setId(9L);
        shoes = variant(200L, "Giày", "Đen", "42");
        shirt = variant(100L, "Áo", "Trắng", "M");
        // 2 giày 500.000 (sau khuyến mãi) + 1 áo 200.000 = 1.200.000; mã giảm 120.000 (10%); phí giao 30.000.
        order = Order.builder()
                .code("ORD-1")
                .user(buyer)
                .store(store)
                .status(OrderStatus.HOAN_THANH)
                .completedAt(LocalDateTime.of(2026, 10, 10, 9, 0))
                .finalAmount(1_110_000.0)
                .shippingFee(30_000.0)
                .receiverPhone("0901234567")
                .items(new ArrayList<>(List.of(item(1L, shoes, 2, 1_000_000), item(2L, shirt, 1, 200_000))))
                .build();
        order.setId(50L);
        when(orders.findById(50L)).thenReturn(Optional.of(order));
        when(accessGuard.currentUser()).thenReturn(buyer);
        when(returns.save(any(ReturnRequest.class))).thenAnswer(inv -> {
            ReturnRequest r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(900L);
            }
            return r;
        });
        when(returns.returnedQuantities(anyLong(), any())).thenReturn(List.of());
        when(accessGuard.storeOf(staff)).thenReturn(store);
        when(accessGuard.storeOf(owner)).thenReturn(store);
    }

    private static ProductVariant variant(long id, String name, String color, String size) {
        ProductVariant v = ProductVariant.builder()
                .product(Product.builder().name(name).build())
                .color(color)
                .size(size)
                .build();
        v.setId(id);
        return v;
    }

    private static OrderItem item(long id, ProductVariant v, int quantity, double lineTotal) {
        OrderItem item = OrderItem.builder()
                .productVariant(v)
                .quantity(quantity)
                .lineTotal(lineTotal)
                .unitPrice(lineTotal / quantity)
                .costPrice(300_000.0)
                .build();
        item.setId(id);
        return item;
    }

    private static ReturnCreateRequest request(long variantId, int quantity) {
        return ReturnCreateRequest.builder()
                .orderId(50L)
                .reason("  Sai kích cỡ ")
                .items(List.of(new ReturnCreateRequest.Line(variantId, quantity)))
                .build();
    }

    @Test
    void refundSuggestionSharesTheOrderDiscountAndSkipsShipping() {
        ReturnResponse res = service.create(request(200L, 1));
        assertEquals(ReturnStatus.CHO_DUYET, res.getStatus());
        assertEquals("Sai kích cỡ", res.getReason());
        // Khách trả 1.080.000 cho 1.200.000 tiền hàng: mỗi đôi giày 500.000 -> 450.000.
        assertEquals(450_000.0, res.getSuggestedRefund());
        assertEquals(450_000.0, res.getItems().getFirst().getUnitRefund());
        assertTrue(res.getCode().startsWith("TH-261015-"));
        assertEquals(List.of(ReturnServiceImpl.CANCEL), res.getAllowedActions());
        verify(notifications).notifyStore(eq(store), eq(NotificationType.RETURN_REQUESTED), any(), anyString(), eq(900L));
    }

    @Test
    void cannotReturnMoreThanBoughtOrTwiceOrAfterTheDeadline() {
        when(returns.returnedQuantities(eq(50L), any())).thenReturn(List.<Object[]>of(new Object[] {1L, 1L}));
        AppException tooMany = assertThrows(AppException.class, () -> service.create(request(200L, 2)));
        assertTrue(tooMany.getMessage().contains("Chỉ còn trả được 1"), tooMany.getMessage());
        AppException notInOrder = assertThrows(AppException.class, () -> service.create(request(999L, 1)));
        assertEquals(ErrorCode.BAD_REQUEST, notInOrder.getErrorCode());

        when(returns.existsByOrder_IdAndStatusIn(eq(50L), any())).thenReturn(true);
        AppException open = assertThrows(AppException.class, () -> service.create(request(100L, 1)));
        assertTrue(open.getMessage().contains("đang có yêu cầu"));

        when(returns.existsByOrder_IdAndStatusIn(eq(50L), any())).thenReturn(false);
        order.setCompletedAt(LocalDateTime.of(2026, 10, 7, 9, 0));
        AppException late = assertThrows(AppException.class, () -> service.create(request(100L, 1)));
        assertTrue(late.getMessage().contains("quá hạn"));

        order.setCompletedAt(LocalDateTime.of(2026, 10, 14, 9, 0));
        order.setStatus(OrderStatus.DANG_GIAO);
        assertThrows(AppException.class, () -> service.create(request(100L, 1)));
        verify(returns, never()).save(any());
    }

    @Test
    void onlyTheBuyerMayAskForAReturn() {
        User other = User.builder().username("khach002").role(Role.USER).build();
        other.setId(70L);
        when(accessGuard.currentUser()).thenReturn(other);
        assertThrows(AppException.class, () -> service.create(request(100L, 1)));
        when(accessGuard.currentUser()).thenReturn(staff);
        assertThrows(AppException.class, () -> service.create(request(100L, 1)));
    }

    private ReturnRequest pending() {
        ReturnRequest r = ReturnRequest.builder()
                .code("TH-261015-ABCD")
                .order(order)
                .store(store)
                .user(buyer)
                .status(ReturnStatus.CHO_DUYET)
                .reason("Sai kích cỡ")
                .suggestedRefund(450_000.0)
                .build();
        r.setId(900L);
        r.getItems().add(ReturnItem.builder().request(r).orderItem(order.getItems().getFirst()).quantity(1)
                .unitRefund(450_000.0).build());
        when(returns.findById(900L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void staffApprovesOrRejectsWithAReasonAndOnlyManagersRefund() {
        ReturnRequest r = pending();
        when(accessGuard.authorize(store, Access.STAFF_WRITE)).thenReturn(staff);
        when(accessGuard.currentUser()).thenReturn(staff);
        ReturnResponse approved = service.approve(900L, null);
        assertEquals(ReturnStatus.DA_DUYET, approved.getStatus());
        assertEquals(staff, r.getDecidedBy());
        assertEquals(List.of(ReturnServiceImpl.REJECT), approved.getAllowedActions(), "nhân viên không hoàn tiền");
        verify(notifications).notify(eq(buyer), eq(NotificationType.RETURN_UPDATED), any(), anyString(), eq(900L));

        AppException noReason = assertThrows(AppException.class, () -> service.reject(900L, new ReturnDecisionRequest(" ", null)));
        assertEquals(ErrorCode.BAD_REQUEST, noReason.getErrorCode());
        ReturnResponse rejected = service.reject(900L, new ReturnDecisionRequest("Giày đã qua sử dụng", null));
        assertEquals(ReturnStatus.TU_CHOI, rejected.getStatus());
        assertEquals("Giày đã qua sử dụng", rejected.getStoreNote());
        assertThrows(AppException.class, () -> service.approve(900L, null), "đã từ chối thì không duyệt lại");
    }

    @Test
    void refundPutsGoodsBackInStockAndRecordsTheAmount() {
        ReturnRequest r = pending();
        when(accessGuard.authorize(store, Access.MANAGER_WRITE)).thenReturn(owner);

        AppException tooMuch = assertThrows(AppException.class,
                () -> service.refund(900L, new ReturnDecisionRequest(null, 2_000_000.0)));
        assertEquals(ErrorCode.BAD_REQUEST, tooMuch.getErrorCode());

        ReturnResponse res = service.refund(900L, new ReturnDecisionRequest("Đã chuyển khoản", null));
        assertEquals(ReturnStatus.DA_HOAN_TIEN, res.getStatus());
        assertEquals(450_000.0, r.getRefundAmount(), "không nhập thì hoàn đúng số gợi ý");
        assertEquals(LocalDateTime.of(2026, 10, 15, 12, 0), r.getRefundedAt());
        assertEquals(owner, r.getDecidedBy(), "hoàn thẳng khi còn chờ duyệt thì ghi người hoàn là người quyết");
        verify(stock).restock(eq(shoes), eq(1), eq(300_000.0), any(StockService.Ref.class), eq("Khách trả hàng TH-261015-ABCD"),
                eq(owner));
        assertThrows(AppException.class, () -> service.refund(900L, null), "không hoàn hai lần");
    }

    @Test
    void customerCancelsOnlyWhilePending() {
        ReturnRequest r = pending();
        assertEquals(ReturnStatus.DA_HUY, service.cancel(900L).getStatus());
        assertEquals(ReturnStatus.DA_HUY, service.cancel(900L).getStatus(), "bấm lại không lỗi");

        r.setStatus(ReturnStatus.DA_DUYET);
        assertThrows(AppException.class, () -> service.cancel(900L));
    }

    @Test
    void paidRatioHandlesFreeAndLegacyOrders() {
        assertEquals(0.9, ReturnServiceImpl.paidRatio(order), 1e-9);
        order.setFinalAmount(30_000.0);
        assertEquals(0, ReturnServiceImpl.paidRatio(order), "đơn được giảm hết tiền hàng thì không hoàn gì");
        OrderItem legacy = OrderItem.builder().quantity(3).unitPrice(100_000.0).build();
        assertEquals(100_000.0, ReturnServiceImpl.unitPaid(legacy));
    }
}
