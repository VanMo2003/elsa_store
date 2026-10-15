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

import java.time.Clock;
import java.time.Instant;
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
    private final OrderServiceImpl service = new OrderServiceImpl(
            orders, customers, addresses, variants, carts, notifications, accessGuard, accounts, pricing, stock);

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
        verify(notifications, never()).statusChanged(any(), any());
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
