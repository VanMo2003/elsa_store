package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.PurchaseOrderRequest;
import com.example.elsa_store.dto.response.PurchaseOrderResponse;
import com.example.elsa_store.entity.*;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.repository.PurchaseOrderRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.repository.SupplierRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PurchaseOrderServiceImplTest {

    private final PurchaseOrderRepository purchases = mock(PurchaseOrderRepository.class);
    private final SupplierRepository suppliers = mock(SupplierRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final StockService stock = mock(StockService.class);
    private final AccessGuard accessGuard = mock(AccessGuard.class);
    private final PurchaseOrderServiceImpl service =
            new PurchaseOrderServiceImpl(purchases, suppliers, variants, stores, stock, accessGuard);

    private Store store;
    private Store otherStore;
    private User owner;
    private ProductVariant shoes;
    private ProductVariant shirt;
    private ProductVariant foreign;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("Store A").build();
        store.setId(1L);
        otherStore = Store.builder().name("Store B").build();
        otherStore.setId(2L);
        owner = User.builder().username("owner01").fullName("Chu A").role(Role.STORE_OWNER).build();
        owner.setId(5L);
        shoes = variant(200L, "Giay", store);
        shirt = variant(100L, "Ao", store);
        foreign = variant(300L, "Mu", otherStore);
        when(accessGuard.requireStoreManager()).thenReturn(store);
        when(accessGuard.currentUser()).thenReturn(owner);
        when(accessGuard.authorize(any(Store.class), any(Access.class))).thenReturn(owner);
        when(purchases.existsByCode(anyString())).thenReturn(false);
        when(purchases.save(any(PurchaseOrder.class))).thenAnswer(inv -> {
            PurchaseOrder p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(77L);
            }
            return p;
        });
        when(stock.levels(any())).thenReturn(Map.of(200L, 4));
    }

    private ProductVariant variant(long id, String name, Store owner) {
        Product product = Product.builder().name(name).store(owner).build();
        product.setId(id / 10);
        ProductVariant v = ProductVariant.builder().product(product).price(500_000.0).build();
        v.setId(id);
        when(variants.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    private static PurchaseOrderRequest.Line line(long variantId, int quantity, double price) {
        return PurchaseOrderRequest.Line.builder().productVariantId(variantId).quantity(quantity).unitPrice(price).build();
    }

    private static PurchaseOrderRequest request(PurchaseOrderRequest.Line... lines) {
        return PurchaseOrderRequest.builder().note("  Nhap dot 1  ").items(new ArrayList<>(List.of(lines))).build();
    }

    private PurchaseOrder stored(int status) {
        PurchaseOrder p = new PurchaseOrder();
        p.setId(77L);
        p.setCode("PN-261014-ABCD");
        p.setStore(store);
        p.setStatus(status);
        PurchaseOrderItem a = new PurchaseOrderItem(p, shoes, 10, 250_000.0);
        PurchaseOrderItem b = new PurchaseOrderItem(p, shirt, 5, 80_000.0);
        p.setItems(new ArrayList<>(List.of(a, b)));
        when(purchases.findByIdForUpdate(77L)).thenReturn(Optional.of(p));
        when(purchases.findById(77L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void draftIsCreatedWithCodeTotalAndTrimmedNote() {
        PurchaseOrderResponse res = service.create(request(line(200L, 10, 250_000), line(100L, 5, 80_000.4)));

        assertTrue(res.getCode().matches("PN-\\d{6}-[A-Z2-9]{4}"), res.getCode());
        assertEquals(0, res.getStatus());
        assertEquals("DRAFT", res.getStatusName());
        assertEquals("Nhap dot 1", res.getNote());
        assertEquals(10 * 250_000 + 5 * 80_000, res.getTotalAmount(), "giá nhập làm tròn đồng");
        assertEquals(15, res.getTotalQuantity());
        assertEquals(2, res.getItemCount());
        assertEquals("Chu A", res.getCreatedByName());
        assertEquals(4, res.getItems().stream().filter(l -> l.getProductVariantId() == 200L).findFirst()
                .orElseThrow().getCurrentStock());
        verify(stock, never()).receive(any(), anyInt(), anyDouble(), any(), any());
    }

    @Test
    void linesMustBeUniqueAndBelongToTheStore() {
        AppException duplicate = assertThrows(AppException.class,
                () -> service.create(request(line(200L, 1, 1), line(200L, 2, 1))));
        assertEquals(ErrorCode.BAD_REQUEST, duplicate.getErrorCode());

        AppException foreignLine = assertThrows(AppException.class,
                () -> service.create(request(line(300L, 1, 1))));
        assertEquals("Product belongs to another store", foreignLine.getMessage());
        verify(purchases, never()).save(any());
    }

    @Test
    void supplierMustBeOneOfTheStores() {
        Supplier theirs = Supplier.builder().name("NCC B").store(otherStore).build();
        theirs.setId(9L);
        when(suppliers.findById(9L)).thenReturn(Optional.of(theirs));
        PurchaseOrderRequest req = request(line(200L, 1, 1));
        req.setSupplierId(9L);

        AppException ex = assertThrows(AppException.class, () -> service.create(req));
        assertEquals("Supplier belongs to another store", ex.getMessage());
    }

    @Test
    void receivingAddsStockLineByLineInVariantOrderAndLocksThePurchase() {
        PurchaseOrder p = stored(PurchaseOrder.DRAFT);

        PurchaseOrderResponse res = service.receive(77L);

        InOrder inOrder = inOrder(stock);
        inOrder.verify(stock).receive(eq(shirt), eq(5), eq(80_000.0), any(StockService.Ref.class), eq(owner));
        inOrder.verify(stock).receive(eq(shoes), eq(10), eq(250_000.0), any(StockService.Ref.class), eq(owner));
        assertEquals("RECEIVED", res.getStatusName());
        assertNotNull(p.getReceivedAt());
        verify(purchases).findByIdForUpdate(77L);
    }

    @Test
    void receivedPurchaseCannotBeReceivedEditedCancelledOrDeleted() {
        stored(PurchaseOrder.RECEIVED);

        assertEquals(ErrorCode.PURCHASE_ORDER_LOCKED,
                assertThrows(AppException.class, () -> service.receive(77L)).getErrorCode());
        assertEquals(ErrorCode.PURCHASE_ORDER_LOCKED,
                assertThrows(AppException.class, () -> service.update(77L, request(line(200L, 1, 1)))).getErrorCode());
        assertEquals(ErrorCode.PURCHASE_ORDER_LOCKED,
                assertThrows(AppException.class, () -> service.cancel(77L)).getErrorCode());
        assertEquals(ErrorCode.PURCHASE_ORDER_LOCKED,
                assertThrows(AppException.class, () -> service.delete(77L)).getErrorCode());
        verify(stock, never()).receive(any(), anyInt(), anyDouble(), any(), any());
        verify(purchases, never()).delete(any());
    }

    @Test
    void cancelledPurchaseNeverReachesStock() {
        stored(PurchaseOrder.CANCELLED);
        assertEquals("CANCELLED", service.cancel(77L).getStatusName(), "huỷ lại lần nữa không lỗi");
        assertThrows(AppException.class, () -> service.receive(77L));
        service.delete(77L);
        verify(purchases).delete(any());
        verify(stock, never()).receive(any(), anyInt(), anyDouble(), any(), any());
    }

    @Test
    void editingADraftReplacesItsLines() {
        PurchaseOrder p = stored(PurchaseOrder.DRAFT);
        List<PurchaseOrderItem> managed = p.getItems();

        PurchaseOrderResponse res = service.update(77L, request(line(200L, 2, 260_000)));

        assertSame(managed, p.getItems(), "sửa trong chính danh sách để xoá được dòng cũ");
        assertEquals(1, p.getItems().size());
        assertEquals(520_000, res.getTotalAmount());
    }

    @Test
    void writeAccessIsCheckedOnTheStoreOfThePurchase() {
        stored(PurchaseOrder.DRAFT);
        when(accessGuard.authorize(any(Store.class), eq(Access.MANAGER_WRITE)))
                .thenThrow(new AppException(ErrorCode.UN_AUTHORIZED));
        assertThrows(AppException.class, () -> service.receive(77L));
        verify(stock, never()).receive(any(), anyInt(), anyDouble(), any(), any());
    }
}
