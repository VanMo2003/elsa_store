package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.OrderRequest;
import com.example.elsa_store.dto.response.OrderQuoteResponse;
import com.example.elsa_store.dto.response.OrderResponse;
import com.example.elsa_store.entity.*;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.*;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.OrderNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderServiceImplTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final AddressRepository addresses = mock(AddressRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final CartRepository carts = mock(CartRepository.class);
    private final OrderNotificationService notifications = mock(OrderNotificationService.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final UserAccountFactory accounts = mock(UserAccountFactory.class);
    private final StockService stock = mock(StockService.class);
    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final PromotionProductRepository promotionLinks = mock(PromotionProductRepository.class);
    private final PricingService pricing = new PricingService(
            promotions, promotionLinks, Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneId.of("UTC")));
    private final OrderTimeline timeline = mock(OrderTimeline.class);
    private final StockAlerts stockAlerts = mock(StockAlerts.class);
    private final OrderExcelExporter excel = mock(OrderExcelExporter.class);
    private final OrderServiceImpl service = new OrderServiceImpl(
            orders, customers, addresses, variants, carts, notifications, accessGuard, accounts, pricing, stock, timeline,
            stockAlerts, excel, new ReturnPolicy(7));

    private User buyer;
    private User staff;
    private Store store;
    private Customer customer;
    private ProductVariant shoes;
    private ProductVariant shirt;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("Store A").approvalStatus(ApprovalStatus.APPROVED).build();
        store.setId(1L);
        buyer = User.builder().username("khach001").role(Role.USER).build();
        buyer.setId(7L);
        staff = User.builder().username("saStaff01").role(Role.STAFF).store(store).build();
        staff.setId(8L);
        customer = Customer.builder().fullName("Khach 001").user(buyer).build();
        customer.setId(70L);
        Product p1 = Product.builder().name("Giay").basePrice(500_000.0).status(1).store(store).build();
        p1.setId(10L);
        Product p2 = Product.builder().name("Ao").basePrice(100_000.0).status(1).store(store).build();
        p2.setId(11L);
        shoes = ProductVariant.builder().product(p1).price(500_000.0).color("Den").size("42").build();
        shoes.setId(200L);
        shirt = ProductVariant.builder().product(p2).price(100_000.0).color("Trang").size("M").build();
        shirt.setId(100L);
        when(variants.findById(200L)).thenReturn(Optional.of(shoes));
        when(variants.findById(100L)).thenReturn(Optional.of(shirt));
        when(accessGuard.currentUser()).thenReturn(buyer);
        when(accessGuard.storeOf(staff)).thenReturn(store);
        when(accessGuard.requireStoreMember()).thenReturn(store);
        when(accounts.ensureCustomerProfile(buyer)).thenReturn(customer);
        when(orders.existsByCode(anyString())).thenReturn(false);
        when(orders.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(500L);
            }
            return o;
        });
        when(promotionLinks.findActiveAutomatic(any(), any())).thenReturn(List.of());
        when(promotions.findActiveStoreWide(anyLong(), any())).thenReturn(List.of());
        when(stock.reserve(eq(shoes), anyInt())).thenReturn(true);
        when(stock.reserve(eq(shirt), anyInt())).thenReturn(false); // không theo dõi kho
    }

    private static OrderRequest.OrderItemRequest item(long variantId, int quantity) {
        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest();
        item.setProductVariantId(variantId);
        item.setQuantity(quantity);
        return item;
    }

    private OrderRequest customerOrder() {
        return OrderRequest.builder()
                .receiverName("Nguyen Van A")
                .receiverPhone("0901234567")
                .shippingAddress("1 Nguyen Hue, Q1, TP HCM")
                .items(new ArrayList<>(List.of(item(200L, 1), item(100L, 2), item(200L, 1))))
                .build();
    }

    @Test
    void customerOrderIsPricedOnTheServerMergedAndReservedInVariantOrder() {
        OrderResponse res = service.create(customerOrder());

        assertTrue(res.getCode().matches("ORD-\\d{6}-[A-Z2-9]{6}"), res.getCode());
        assertEquals(1_200_000, res.getFinalAmount());
        assertEquals(1_200_000, res.getTotalAmount());
        assertEquals(0, res.getDiscountAmount());
        assertEquals("COD", res.getPaymentMethod());
        assertEquals(PaymentStatus.CHUA_THANH_TOAN, res.getPaymentStatus());
        assertEquals("Nguyen Van A", res.getReceiverName());
        assertEquals(2, res.getItems().size(), "hai dòng cùng biến thể được gộp");
        // Trừ kho theo id biến thể tăng dần (100 rồi 200) để không deadlock.
        InOrder inOrder = inOrder(stock);
        inOrder.verify(stock).reserve(shirt, 2);
        inOrder.verify(stock).reserve(shoes, 2);
        verify(notifications).orderPlaced(any());
        assertEquals(List.of(OrderStatus.DA_HUY), res.getAllowedNextStatuses());
        verify(stock).recordSale(any(Order.class), eq(buyer));
    }

    @Test
    void orderLinesKeepTheCostPriceOfTheMomentOfSale() {
        shoes.setCostPrice(300_000.0);
        service.create(customerOrder());

        var saved = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orders).save(saved.capture());
        OrderItem shoesLine = saved.getValue().getItems().stream()
                .filter(i -> i.getProductVariant() == shoes).findFirst().orElseThrow();
        OrderItem shirtLine = saved.getValue().getItems().stream()
                .filter(i -> i.getProductVariant() == shirt).findFirst().orElseThrow();
        assertEquals(300_000.0, shoesLine.getCostPrice());
        assertNull(shirtLine.getCostPrice(), "chưa có giá vốn thì để trống, không đoán");

        shoes.setCostPrice(350_000.0);
        assertEquals(300_000.0, shoesLine.getCostPrice(), "giá vốn đổi về sau không làm đổi lợi nhuận đơn cũ");
    }

    @Test
    void customerMustGiveShippingDetails() {
        OrderRequest noAddress = customerOrder();
        noAddress.setShippingAddress(" ");
        AppException ex = assertThrows(AppException.class, () -> service.create(noAddress));
        assertEquals("shippingAddress: must not be blank", ex.getMessage());

        OrderRequest badPhone = customerOrder();
        badPhone.setReceiverPhone("abc");
        assertEquals("receiverPhone: invalid value",
                assertThrows(AppException.class, () -> service.create(badPhone)).getMessage());
        verify(stock, never()).reserve(any(), anyInt());
    }

    @Test
    void savedAddressIsCopiedIntoTheOrder() {
        Address home = Address.builder()
                .customer(customer).fullName("Tran B").phone("0912345678")
                .detail("12 Le Loi").ward("Ben Nghe").district("Quan 1").province("TP HCM")
                .build();
        home.setId(5L);
        when(addresses.findById(5L)).thenReturn(Optional.of(home));
        OrderRequest req = OrderRequest.builder().addressId(5L).items(List.of(item(100L, 1))).build();

        OrderResponse res = service.create(req);
        assertEquals("Tran B", res.getReceiverName());
        assertEquals("0912345678", res.getReceiverPhone());
        assertEquals("12 Le Loi, Ben Nghe, Quan 1, TP HCM", res.getShippingAddress());
    }

    @Test
    void outOfStockStopsTheOrder() {
        when(stock.reserve(eq(shoes), anyInt())).thenThrow(StockService.notEnough(shoes, 1));
        AppException ex = assertThrows(AppException.class, () -> service.create(customerOrder()));
        assertEquals(ErrorCode.OUT_OF_STOCK, ex.getErrorCode());
        assertEquals("Sản phẩm \"Giay\" (Den / 42) chỉ còn 1", ex.getMessage());
        verify(orders, never()).save(any());
    }

    @Test
    void vnpayOrdersWaitForPaymentAndCartItemsAreRemoved() {
        Cart cart = Cart.builder().user(buyer).items(new ArrayList<>()).totalAmount(0.0).build();
        cart.getItems().add(CartItem.builder().cart(cart).productVariant(shoes).quantity(1).lineTotal(500_000.0).build());
        cart.getItems().add(CartItem.builder().cart(cart).productVariant(shirt).quantity(1).lineTotal(100_000.0).build());
        when(carts.findByUserId(7L)).thenReturn(Optional.of(cart));
        OrderRequest req = customerOrder();
        req.setItems(List.of(item(200L, 1)));
        req.setPaymentMethod(1L);
        req.setFromCart(true);

        OrderResponse res = service.create(req);
        assertEquals("VNPAY", res.getPaymentMethod());
        assertEquals(PaymentStatus.CHO_THANH_TOAN, res.getPaymentStatus());
        assertEquals(1, cart.getItems().size());
        assertEquals(shirt, cart.getItems().getFirst().getProductVariant());
        assertEquals(100_000, cart.getTotalAmount());
    }

    private Order placed(OrderStatus status, PaymentStatus payment) {
        Order order = new Order();
        order.setId(900L);
        order.setStore(store);
        order.setUser(buyer);
        order.setStatus(status);
        order.setPaymentStatus(payment);
        OrderItem tracked = new OrderItem();
        tracked.setProductVariant(shoes);
        tracked.setQuantity(2);
        tracked.setStockReserved(true);
        OrderItem untracked = new OrderItem();
        untracked.setProductVariant(shirt);
        untracked.setQuantity(1);
        untracked.setStockReserved(false);
        order.setItems(new ArrayList<>(List.of(tracked, untracked)));
        when(orders.findById(900L)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void statusFollowsTheAllowedFlow() {
        when(accessGuard.currentUser()).thenReturn(staff);
        Order order = placed(OrderStatus.HOAN_THANH, PaymentStatus.DA_THANH_TOAN);
        AppException ex = assertThrows(AppException.class, () -> service.updateStatus(900L, OrderStatus.CHUA_XAC_NHAN));
        assertEquals(ErrorCode.INVALID_ORDER_TRANSITION, ex.getErrorCode());

        order.setStatus(OrderStatus.DA_HUY);
        assertThrows(AppException.class, () -> service.updateStatus(900L, OrderStatus.HOAN_THANH));
        verify(notifications, never()).statusChanged(any(), any(), any());
        verify(timeline, never()).record(any(), any(), any(), any());
    }

    @Test
    void cancellingReturnsTheStockOfTheOrder() {
        when(accessGuard.currentUser()).thenReturn(staff);
        Order order = placed(OrderStatus.DA_XAC_NHAN, PaymentStatus.CHUA_THANH_TOAN);

        OrderResponse res = service.updateStatus(900L, OrderStatus.DA_HUY);
        assertEquals(OrderStatus.DA_HUY, res.getStatus());
        // Hoàn kho từng dòng đã trừ (và ghi thẻ kho) là việc của StockService — xem StockServiceTest.
        verify(stock).releaseOrder(order, "Huỷ đơn", staff);
        assertNull(res.getCompletedAt());
        assertTrue(res.getAllowedNextStatuses().isEmpty());
    }

    @Test
    void completingACashOnDeliveryOrderMarksItPaidAndStampsTheRevenueDate() {
        when(accessGuard.currentUser()).thenReturn(staff);
        Order order = placed(OrderStatus.DA_XAC_NHAN, PaymentStatus.CHUA_THANH_TOAN);
        order.setOrderDate(java.time.LocalDateTime.now().minusDays(3));
        OrderResponse res = service.updateStatus(900L, OrderStatus.HOAN_THANH);
        assertEquals(PaymentStatus.DA_THANH_TOAN, res.getPaymentStatus());
        verify(stock, never()).releaseOrder(any(), any(), any());
        // Doanh thu ghi nhận vào ngày giao xong, không phải ngày đặt.
        assertNotNull(order.getCompletedAt());
        assertTrue(order.getCompletedAt().isAfter(order.getOrderDate()));
        assertEquals(order.getCompletedAt(), res.getCompletedAt());
    }

    @Test
    void staffSeesOnlyTheButtonsTheyMayUse() {
        when(accessGuard.currentUser()).thenReturn(staff);
        placed(OrderStatus.CHUA_XAC_NHAN, PaymentStatus.CHO_THANH_TOAN);
        OrderResponse waiting = service.getById(900L);
        assertEquals(List.of(OrderStatus.DA_XAC_NHAN, OrderStatus.DA_HUY), waiting.getAllowedNextStatuses());

        placed(OrderStatus.DA_XAC_NHAN, PaymentStatus.DA_THANH_TOAN);
        OrderResponse paid = service.getById(900L);
        assertEquals(List.of(OrderStatus.HOAN_THANH), paid.getAllowedNextStatuses(), "đã thanh toán thì không huỷ");
    }

    @Test
    void shippingStepIsOfferedOnlyForOrdersWithADeliveryAddress() {
        when(accessGuard.currentUser()).thenReturn(staff);
        Order counter = placed(OrderStatus.DA_XAC_NHAN, PaymentStatus.CHUA_THANH_TOAN);
        assertEquals(List.of(OrderStatus.HOAN_THANH, OrderStatus.DA_HUY), service.getById(900L).getAllowedNextStatuses());
        AppException ex = assertThrows(AppException.class, () -> service.updateStatus(900L, OrderStatus.DANG_GIAO));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertEquals(OrderStatus.DA_XAC_NHAN, counter.getStatus());

        Order online = placed(OrderStatus.DA_XAC_NHAN, PaymentStatus.CHUA_THANH_TOAN);
        online.setShippingAddress("1 Nguyen Hue, Q1");
        assertEquals(List.of(OrderStatus.DANG_GIAO, OrderStatus.HOAN_THANH, OrderStatus.DA_HUY),
                service.getById(900L).getAllowedNextStatuses());
        OrderResponse res = service.updateStatus(900L, OrderStatus.DANG_GIAO);
        assertEquals(OrderStatus.DANG_GIAO, res.getStatus());
        assertEquals(List.of(OrderStatus.HOAN_THANH, OrderStatus.DA_HUY), res.getAllowedNextStatuses());
        verify(timeline).record(online, OrderStatus.DANG_GIAO, staff, null);
        verify(notifications).statusChanged(online, OrderStatus.DA_XAC_NHAN, staff);
        verify(stock, never()).releaseOrder(any(), any(), any());
    }

    @Test
    void customerConfirmsReceiptOnlyWhileTheOrderIsBeingDelivered() {
        Order order = placed(OrderStatus.DANG_GIAO, PaymentStatus.CHUA_THANH_TOAN);
        order.setShippingAddress("1 Nguyen Hue, Q1");
        assertEquals(List.of(OrderStatus.HOAN_THANH), service.getById(900L).getAllowedNextStatuses());
        // Đang giao thì khách không tự huỷ được nữa.
        assertThrows(AppException.class, () -> service.updateStatus(900L, OrderStatus.DA_HUY));

        OrderResponse res = service.updateStatus(900L, OrderStatus.HOAN_THANH);
        assertEquals(OrderStatus.HOAN_THANH, res.getStatus());
        assertEquals(PaymentStatus.DA_THANH_TOAN, res.getPaymentStatus(), "COD giao xong là đã thu tiền");
        assertNotNull(order.getCompletedAt());
        verify(notifications).statusChanged(order, OrderStatus.DANG_GIAO, buyer);
        // Bấm lại không lỗi, không ghi thêm mốc.
        service.updateStatus(900L, OrderStatus.HOAN_THANH);
        verify(timeline, times(1)).record(any(), any(), any(), any());

        User stranger = User.builder().username("khach002").role(Role.USER).build();
        stranger.setId(9L);
        when(accessGuard.currentUser()).thenReturn(stranger);
        placed(OrderStatus.DANG_GIAO, PaymentStatus.CHUA_THANH_TOAN).setShippingAddress("x");
        assertThrows(AppException.class, () -> service.updateStatus(900L, OrderStatus.HOAN_THANH));
    }

    @Test
    void cancelReasonIsTrimmedStoredAndPutOnTheTimeline() {
        when(accessGuard.currentUser()).thenReturn(staff);
        Order order = placed(OrderStatus.DANG_GIAO, PaymentStatus.CHUA_THANH_TOAN);
        order.setShippingAddress("1 Nguyen Hue, Q1");
        AppException tooLong = assertThrows(AppException.class,
                () -> service.updateStatus(900L, OrderStatus.DA_HUY, "x".repeat(256)));
        assertEquals(ErrorCode.BAD_REQUEST, tooLong.getErrorCode());

        OrderResponse res = service.updateStatus(900L, OrderStatus.DA_HUY, "  Khách không nhận hàng  ");
        assertEquals("Khách không nhận hàng", res.getCancelReason());
        assertEquals("Khách không nhận hàng", order.getCancelReason());
        verify(stock).releaseOrder(order, "Huỷ đơn", staff);
        verify(timeline).record(order, OrderStatus.DA_HUY, staff, "Khách không nhận hàng");
    }

    @Test
    void placingAnOrderStartsItsTimeline() {
        OrderResponse res = service.create(customerOrder());
        verify(timeline).record(any(Order.class), eq(OrderStatus.CHUA_XAC_NHAN), eq(buyer), isNull());
        verify(stockAlerts).afterSale(any(Order.class));
        verify(timeline).entries(any(Order.class), eq(buyer));
        assertEquals(OrderStatus.CHUA_XAC_NHAN, res.getStatus());
    }

    @Test
    void customerOrdersPayTheShippingFeeUntilTheFreeShippingThreshold() {
        store.setShippingFee(30_000.0);
        store.setFreeShippingThreshold(1_000_000.0);
        OrderRequest small = OrderRequest.builder()
                .receiverName("Nguyen Van A")
                .receiverPhone("0901234567")
                .shippingAddress("1 Nguyen Hue, Q1, TP HCM")
                .items(new ArrayList<>(List.of(item(100L, 2))))
                .build();

        OrderQuoteResponse quote = service.quote(small);
        assertEquals(30_000, quote.getShippingFee());
        assertEquals(230_000, quote.getTotal(), "tiền hàng + phí giao");
        assertEquals(800_000.0, quote.getAmountToFreeShipping());
        assertEquals(1_000_000.0, quote.getFreeShippingThreshold());

        OrderResponse placed = service.create(small);
        assertEquals(30_000.0, placed.getShippingFee());
        assertEquals(230_000.0, placed.getFinalAmount());
        assertEquals(200_000.0, placed.getTotalAmount());

        // 2 giày + 2 áo = 1.200.000đ: đủ mức miễn phí giao.
        OrderQuoteResponse big = service.quote(customerOrder());
        assertEquals(0, big.getShippingFee());
        assertNull(big.getAmountToFreeShipping());
        assertEquals(1_200_000, big.getTotal());
    }

    @Test
    void counterSalesAndFreeShippingStoresChargeNoShipping() {
        when(accessGuard.currentUser()).thenReturn(staff);
        store.setShippingFee(30_000.0);
        OrderRequest counter = OrderRequest.builder().items(new ArrayList<>(List.of(item(100L, 1)))).build();
        OrderResponse sold = service.create(counter);
        assertNull(sold.getShippingFee(), "bán tại quầy không có phí giao");
        assertEquals(100_000.0, sold.getFinalAmount());

        when(accessGuard.currentUser()).thenReturn(buyer);
        store.setShippingFee(null);
        OrderQuoteResponse quote = service.quote(customerOrder());
        assertEquals(0, quote.getShippingFee());
        assertNull(quote.getFreeShippingThreshold(), "cửa hàng giao miễn phí thì không có mức miễn phí");
    }

    @Test
    void systemCancelsAnUnpaidOnlineOrderOnlyIfNothingChangedMeanwhile() {
        Order order = placed(OrderStatus.CHUA_XAC_NHAN, PaymentStatus.CHO_THANH_TOAN);
        when(orders.findByIdForUpdate(900L)).thenReturn(Optional.of(order));

        assertTrue(service.autoTransition(900L, OrderStatus.CHUA_XAC_NHAN, OrderStatus.DA_HUY, "Quá hạn thanh toán VNPay"));
        assertEquals(OrderStatus.DA_HUY, order.getStatus());
        assertEquals("Quá hạn thanh toán VNPay", order.getCancelReason());
        verify(stock).releaseOrder(order, "Huỷ đơn", null);
        verify(timeline).record(order, OrderStatus.DA_HUY, null, "Quá hạn thanh toán VNPay");
        verify(notifications).statusChanged(order, OrderStatus.CHUA_XAC_NHAN, null);

        // Lần chạy sau: đơn đã đổi rồi thì không làm gì.
        assertFalse(service.autoTransition(900L, OrderStatus.CHUA_XAC_NHAN, OrderStatus.DA_HUY, "x"));
        // Vừa trả xong thì không huỷ.
        Order paid = placed(OrderStatus.CHUA_XAC_NHAN, PaymentStatus.DA_THANH_TOAN);
        when(orders.findByIdForUpdate(900L)).thenReturn(Optional.of(paid));
        assertFalse(service.autoTransition(900L, OrderStatus.CHUA_XAC_NHAN, OrderStatus.DA_HUY, "x"));
        assertEquals(OrderStatus.CHUA_XAC_NHAN, paid.getStatus());
        verify(stock, times(1)).releaseOrder(any(), any(), any());
    }

    @Test
    void systemCompletesALongDeliveryAndRecordsCashOnDeliveryAsPaid() {
        Order order = placed(OrderStatus.DANG_GIAO, PaymentStatus.CHUA_THANH_TOAN);
        when(orders.findByIdForUpdate(900L)).thenReturn(Optional.of(order));
        assertTrue(service.autoTransition(900L, OrderStatus.DANG_GIAO, OrderStatus.HOAN_THANH, "Tự hoàn thành"));
        assertEquals(OrderStatus.HOAN_THANH, order.getStatus());
        assertEquals(PaymentStatus.DA_THANH_TOAN, order.getPaymentStatus());
        assertNotNull(order.getCompletedAt());

        // Bước không hợp lệ (chờ xác nhận -> hoàn thành) thì từ chối.
        Order fresh = placed(OrderStatus.CHUA_XAC_NHAN, PaymentStatus.CHUA_THANH_TOAN);
        when(orders.findByIdForUpdate(900L)).thenReturn(Optional.of(fresh));
        assertFalse(service.autoTransition(900L, OrderStatus.CHUA_XAC_NHAN, OrderStatus.HOAN_THANH, "x"));
    }

    @Test
    void pagedListIsScopedToWhoIsLookingAndSearchesSafely() {
        when(orders.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.search(null, "  ORD-12 ", null, 0, 500);
        verify(orders).search(isNull(), eq(7L), isNull(), eq("%ord-12%"), isNull(), isNull(),
                eq(PageRequest.of(0, OrderServiceImpl.MAX_PAGE_SIZE)));

        when(accessGuard.currentUser()).thenReturn(staff);
        service.search(OrderStatus.DANG_GIAO, "   ", 99L, -3, 20);
        // Người của cửa hàng luôn bị giới hạn ở cửa hàng của mình, storeId gửi lên bị bỏ qua.
        verify(orders).search(eq(1L), isNull(), eq(OrderStatus.DANG_GIAO), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 20)));

        assertEquals("%50%", OrderServiceImpl.likePattern("50%_\\"));
        assertNull(OrderServiceImpl.likePattern("%_"));
    }

    @Test
    void excelExportIsForManagersAndUsesWholeDays() {
        when(orders.search(any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        when(excel.export(any())).thenReturn(new byte[] {1, 2});

        when(accessGuard.currentUser()).thenReturn(staff);
        when(accessGuard.requireStoreManager()).thenReturn(store);
        assertEquals(2, service.exportExcel(null, "giay", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 15), 99L).length);
        verify(accessGuard).requireStoreManager();
        verify(orders).search(eq(1L), isNull(), isNull(), eq("%giay%"),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)), eq(LocalDateTime.of(2026, 10, 16, 0, 0)),
                eq(PageRequest.of(0, OrderServiceImpl.MAX_EXPORT_ROWS)));

        AppException backwards = assertThrows(AppException.class, () -> service.exportExcel(
                null, null, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 1), null));
        assertEquals(ErrorCode.BAD_REQUEST, backwards.getErrorCode());

        // Khách không tải được danh sách đơn.
        when(accessGuard.currentUser()).thenReturn(buyer);
        AppException customer = assertThrows(AppException.class, () -> service.exportExcel(null, null, null, null, null));
        assertEquals(ErrorCode.UN_AUTHORIZED, customer.getErrorCode());
    }

    @Test
    void pausedStoresTakeNoOnlineOrdersButStillSellAtTheCounter() {
        store.setPaused(true);
        store.setPauseNote("Nghỉ Tết đến 05/02");
        AppException quote = assertThrows(AppException.class, () -> service.quote(customerOrder()));
        assertEquals("Cửa hàng đang tạm nghỉ, chưa nhận đơn mới: Nghỉ Tết đến 05/02", quote.getMessage());
        assertThrows(AppException.class, () -> service.create(customerOrder()));
        verify(orders, never()).save(any());

        when(accessGuard.currentUser()).thenReturn(staff);
        OrderRequest counter = OrderRequest.builder().items(new ArrayList<>(List.of(item(100L, 1)))).build();
        assertEquals(OrderStatus.CHUA_XAC_NHAN, service.create(counter).getStatus());
    }

    @Test
    void countsCoverEveryStatusPlusTheTotal() {
        when(accessGuard.currentUser()).thenReturn(staff);
        when(orders.countByStatus(1L, null)).thenReturn(List.of(
                new Object[] {OrderStatus.CHUA_XAC_NHAN, 3L}, new Object[] {OrderStatus.HOAN_THANH, 5L}));
        Map<String, Long> counts = service.countByStatus(null);
        assertEquals(3L, counts.get("CHUA_XAC_NHAN"));
        assertEquals(0L, counts.get("DANG_GIAO"));
        assertEquals(8L, counts.get("ALL"));
        assertEquals(OrderStatus.values().length + 1, counts.size());
    }

    @Test
    void quoteReportsStockProblemsWithoutReserving() {
        when(stock.levels(any())).thenReturn(Map.of(200L, 1));
        OrderQuoteResponse quote = service.quote(customerOrder());
        assertTrue(quote.isStockProblem());
        OrderQuoteResponse.Line shoesLine = quote.getItems().stream()
                .filter(l -> l.getProductVariantId() == 200L)
                .findFirst()
                .orElseThrow();
        assertTrue(shoesLine.isInsufficientStock());
        assertEquals(1, shoesLine.getAvailableStock());
        verify(stock, never()).reserve(any(), anyInt());
    }
}
