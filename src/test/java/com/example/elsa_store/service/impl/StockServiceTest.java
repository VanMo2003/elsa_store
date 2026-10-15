package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.StockMovementType;
import com.example.elsa_store.entity.*;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StockServiceTest {

    private final InventoryRepository inventories = mock(InventoryRepository.class);
    private final StockMovementRepository movements = mock(StockMovementRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final BackInStock backInStock = mock(BackInStock.class);
    private final StockService service = new StockService(inventories, movements, backInStock);
    private final List<StockMovement> written = new ArrayList<>();

    private Store store;
    private ProductVariant shoes;
    private User manager;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        store = Store.builder().name("Store A").build();
        store.setId(1L);
        Product product = Product.builder().name("Giay").store(store).build();
        product.setId(10L);
        shoes = ProductVariant.builder().product(product).price(500_000.0).build();
        shoes.setId(200L);
        manager = User.builder().username("manager01").build();
        manager.setId(3L);
        when(inventories.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movements.save(any(StockMovement.class))).thenAnswer(inv -> {
            written.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    private Inventory tracked(int quantity) {
        Inventory inventory = Inventory.builder().productVariant(shoes).quantity(quantity).minQuantity(2).build();
        inventory.setId(50L);
        shoes.setInventory(inventory);
        when(inventories.findByProductVariant_Id(200L)).thenReturn(Optional.of(inventory));
        return inventory;
    }

    @Test
    void weightedAverageCostIsRoundedToWholeDong() {
        assertEquals(250_000, StockService.averageCost(0, null, 10, 250_000));
        assertEquals(250_000, StockService.averageCost(5, null, 10, 250_000), "chưa biết giá vốn cũ thì lấy giá nhập");
        assertEquals(260_000, StockService.averageCost(5, 300_000.0, 0 + 5, 220_000));
        // 3 × 100.000 + 7 × 110.000 = 1.070.000 / 10
        assertEquals(107_000, StockService.averageCost(3, 100_000.0, 7, 110_000));
        assertEquals(90_000, StockService.averageCost(-2, 100_000.0, 4, 90_000), "kho âm / hết hàng: lấy giá nhập mới");
        assertEquals(33_333, StockService.averageCost(1, 0.0, 2, 50_000));
    }

    @Test
    void receivingAnUntrackedVariantStartsTrackingAndSetsTheCost() {
        when(inventories.findByProductVariant_Id(200L)).thenReturn(Optional.empty());

        service.receive(shoes, 10, 250_000, new StockService.Ref("PURCHASE_ORDER", 9L, "PN-261014-ABCD"), manager);

        ArgumentCaptor<Inventory> saved = ArgumentCaptor.forClass(Inventory.class);
        verify(inventories).save(saved.capture());
        assertEquals(10, saved.getValue().getQuantity());
        assertSame(saved.getValue(), shoes.getInventory());
        assertEquals(250_000, shoes.getCostPrice());
        verify(entityManager, never()).refresh(any(), any(LockModeType.class));

        StockMovement m = written.getFirst();
        assertEquals(StockMovementType.IMPORT, m.getType());
        assertEquals(10, m.getQuantityChange());
        assertEquals(10, m.getBalanceAfter());
        assertEquals(250_000, m.getUnitCost());
        assertEquals("PN-261014-ABCD", m.getRefCode());
        assertSame(store, m.getStore());
        assertSame(manager, m.getCreatedBy());
    }

    @Test
    void receivingLocksTheRowAndAveragesWithStockOnHand() {
        Inventory inventory = tracked(3);
        shoes.setCostPrice(100_000.0);

        service.receive(shoes, 7, 110_000, StockService.Ref.NONE, manager);

        verify(entityManager).refresh(inventory, LockModeType.PESSIMISTIC_WRITE);
        assertEquals(10, inventory.getQuantity());
        assertEquals(107_000, shoes.getCostPrice());
        assertEquals(10, written.getFirst().getBalanceAfter());
    }

    @Test
    void countingStockRecordsOnlyTheDifference() {
        Inventory inventory = tracked(8);

        service.adjust(shoes, 5, 3, "Hỏng 3 đôi", manager);

        assertEquals(5, inventory.getQuantity());
        assertEquals(3, inventory.getMinQuantity());
        StockMovement m = written.getFirst();
        assertEquals(StockMovementType.ADJUST, m.getType());
        assertEquals(-3, m.getQuantityChange());
        assertEquals(5, m.getBalanceAfter());
        assertEquals("Hỏng 3 đôi", m.getNote());

        written.clear();
        service.adjust(shoes, 5, null, null, manager);
        assertTrue(written.isEmpty(), "đếm lại đúng số cũ thì không ghi thẻ kho");
        assertEquals(3, inventory.getMinQuantity(), "không gửi mức tối thiểu thì giữ nguyên");
    }

    @Test
    void firstCountStartsTracking() {
        when(inventories.findByProductVariant_Id(200L)).thenReturn(Optional.empty());

        service.adjust(shoes, 0, null, null, manager);

        assertNotNull(shoes.getInventory());
        assertEquals(0, shoes.getInventory().getQuantity());
        assertEquals("Bắt đầu theo dõi kho", written.getFirst().getNote());
    }

    @Test
    void stoppingTrackingRemovesTheRowAndLogsWhatWasLeft() {
        Inventory inventory = tracked(4);

        service.stopTracking(shoes, null, manager);

        verify(inventories).delete(inventory);
        assertNull(shoes.getInventory());
        StockMovement m = written.getFirst();
        assertEquals(-4, m.getQuantityChange());
        assertNull(m.getBalanceAfter());
        assertEquals("Ngừng theo dõi kho", m.getNote());
    }

    private Order order() {
        Order order = new Order();
        order.setId(900L);
        order.setCode("ORD-261014-ABCDEF");
        OrderItem taken = new OrderItem();
        taken.setProductVariant(shoes);
        taken.setQuantity(2);
        taken.setCostPrice(300_000.0);
        taken.setStockReserved(true);
        Product shirtProduct = Product.builder().name("Ao").store(store).build();
        ProductVariant shirt = ProductVariant.builder().product(shirtProduct).build();
        shirt.setId(100L);
        OrderItem untracked = new OrderItem();
        untracked.setProductVariant(shirt);
        untracked.setQuantity(1);
        untracked.setStockReserved(false);
        order.setItems(new ArrayList<>(List.of(taken, untracked)));
        return order;
    }

    @Test
    void saleAndReturnAreWrittenOnlyForReservedLines() {
        when(inventories.findStockLevels(any())).thenReturn(List.of(level(200L, 6)));
        Order order = order();

        service.recordSale(order, manager);
        assertEquals(1, written.size());
        assertEquals(StockMovementType.SALE, written.getFirst().getType());
        assertEquals(-2, written.getFirst().getQuantityChange());
        assertEquals(6, written.getFirst().getBalanceAfter());
        assertEquals("ORDER", written.getFirst().getRefType());
        assertEquals(900L, written.getFirst().getRefId());

        written.clear();
        service.releaseOrder(order, "Huỷ đơn", manager);
        verify(inventories).increment(200L, 2);
        verify(inventories, never()).increment(eq(100L), anyInt());
        assertEquals(1, written.size());
        assertEquals(StockMovementType.RETURN, written.getFirst().getType());
        assertEquals(2, written.getFirst().getQuantityChange());
        assertFalse(order.getItems().getFirst().getStockReserved());

        written.clear();
        service.releaseOrder(order, "Huỷ đơn", manager);
        assertTrue(written.isEmpty(), "không hoàn kho hai lần");
        verify(inventories, times(1)).increment(200L, 2);
    }

    private static InventoryRepository.StockLevel level(long variantId, int quantity) {
        return new InventoryRepository.StockLevel() {
            @Override
            public Long getVariantId() {
                return variantId;
            }

            @Override
            public Integer getQuantity() {
                return quantity;
            }
        };
    }
}
