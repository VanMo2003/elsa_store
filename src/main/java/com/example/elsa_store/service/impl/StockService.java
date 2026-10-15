package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.StockMovementType;
import com.example.elsa_store.entity.Inventory;
import com.example.elsa_store.entity.Order;
import com.example.elsa_store.entity.OrderItem;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.PurchaseOrder;
import com.example.elsa_store.entity.StockMovement;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.InventoryRepository;
import com.example.elsa_store.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tồn kho gắn với bán hàng. Biến thể <b>chưa có dòng tồn kho</b> là "không theo dõi kho" — vẫn bán được, không trừ (dữ
 * liệu cũ và cửa hàng chưa nhập kho vẫn chạy như trước; trợ lý AI cũng hiểu là "chưa cập nhật tồn kho"). Biến thể đã có
 * dòng tồn kho thì phải đủ hàng mới đặt được.
 *
 * <p>Mọi thay đổi số lượng (bán, hoàn, nhập, kiểm kê) đều ghi một dòng thẻ kho ({@link StockMovement}).
 */
@Service
public class StockService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public StockService(InventoryRepository inventoryRepository, StockMovementRepository movementRepository) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
    }

    /** Chứng từ gốc của một dòng thẻ kho. */
    public record Ref(String type, Long id, String code) {
        public static final Ref NONE = new Ref(null, null, null);

        public static Ref order(Order order) {
            return new Ref("ORDER", order.getId(), order.getCode());
        }

        public static Ref purchase(PurchaseOrder purchase) {
            return new Ref("PURCHASE_ORDER", purchase.getId(), purchase.getCode());
        }
    }

    /** Tồn kho hiện tại theo id biến thể; biến thể không theo dõi kho không có trong kết quả. */
    public Map<Long, Integer> levels(Collection<Long> variantIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (variantIds == null || variantIds.isEmpty()) {
            return result;
        }
        for (InventoryRepository.StockLevel level : inventoryRepository.findStockLevels(variantIds)) {
            result.put(level.getVariantId(), level.getQuantity() == null ? 0 : Math.max(0, level.getQuantity()));
        }
        return result;
    }

    /**
     * Trừ kho khi đặt đơn. Trả {@code true} nếu đã trừ (để hoàn lại khi huỷ), {@code false} nếu biến thể không theo dõi
     * kho. Không đủ hàng thì ném lỗi nói rõ còn bao nhiêu.
     */
    public boolean reserve(ProductVariant variant, int quantity) {
        Integer available = levels(java.util.List.of(variant.getId())).get(variant.getId());
        if (available == null) {
            return false;
        }
        if (inventoryRepository.decrement(variant.getId(), quantity) == 0) {
            throw notEnough(variant, available);
        }
        return true;
    }

    /** Hoàn kho cho dòng hàng đã trừ (đơn bị huỷ / xoá trước khi giao). */
    public void release(ProductVariant variant, int quantity) {
        if (quantity > 0) {
            inventoryRepository.increment(variant.getId(), quantity);
        }
    }

    /** Kiểm tra mềm (giỏ hàng, báo giá): không trừ gì, chỉ báo lỗi nếu số lượng vượt tồn kho đang theo dõi. */
    public void requireAvailable(ProductVariant variant, int quantity) {
        Integer available = levels(java.util.List.of(variant.getId())).get(variant.getId());
        if (available != null && quantity > available) {
            throw notEnough(variant, available);
        }
    }

    // ------------------------------------------------------------------ nhập hàng, kiểm kê

    /**
     * Nhập hàng: cộng kho (biến thể chưa theo dõi kho thì bắt đầu theo dõi từ số nhập), tính lại giá vốn theo bình quân
     * gia quyền và ghi thẻ kho. Dòng tồn kho bị khoá trong lúc tính để không lệch với đơn đang đặt cùng lúc.
     */
    public void receive(ProductVariant variant, int quantity, double unitCost, Ref ref, User actor) {
        Inventory inventory = lockOrCreate(variant);
        int onHand = Math.max(0, nz(inventory.getQuantity()));
        variant.setCostPrice(averageCost(onHand, variant.getCostPrice(), quantity, unitCost));
        inventory.setQuantity(nz(inventory.getQuantity()) + quantity);
        inventoryRepository.save(inventory);
        write(variant, StockMovementType.IMPORT, quantity, inventory.getQuantity(), unitCost, ref, null, actor);
    }

    /**
     * Kiểm kê: đặt tồn kho bằng số đếm thực tế, ghi phần chênh lệch vào thẻ kho. Biến thể chưa theo dõi kho thì bắt đầu
     * theo dõi. {@code minQuantity} null = giữ nguyên mức cảnh báo.
     */
    public Inventory adjust(ProductVariant variant, int counted, Integer minQuantity, String note, User actor) {
        boolean tracked = inventoryRepository.findByProductVariant_Id(variant.getId()).isPresent();
        Inventory inventory = lockOrCreate(variant);
        int change = counted - (tracked ? nz(inventory.getQuantity()) : 0);
        inventory.setQuantity(counted);
        if (minQuantity != null) {
            inventory.setMinQuantity(minQuantity);
        }
        inventory = inventoryRepository.save(inventory);
        if (change != 0 || !tracked) {
            String reason = note != null ? note : tracked ? "Kiểm kê" : "Bắt đầu theo dõi kho";
            write(variant, StockMovementType.ADJUST, change, counted, variant.getCostPrice(), Ref.NONE, reason, actor);
        }
        return inventory;
    }

    /** Ngừng theo dõi kho: biến thể lại bán không giới hạn như trước khi nhập kho. */
    public void stopTracking(ProductVariant variant, String note, User actor) {
        Inventory inventory = inventoryRepository.findByProductVariant_Id(variant.getId()).orElse(null);
        if (inventory == null) {
            return;
        }
        entityManager.refresh(inventory, LockModeType.PESSIMISTIC_WRITE);
        int left = nz(inventory.getQuantity());
        variant.setInventory(null);
        inventoryRepository.delete(inventory);
        write(variant, StockMovementType.ADJUST, -left, null, variant.getCostPrice(), Ref.NONE,
                note != null ? note : "Ngừng theo dõi kho", actor);
    }

    /** Bình quân gia quyền, làm tròn đồng. Chưa có giá vốn hoặc đang hết hàng thì lấy luôn giá nhập mới. */
    static double averageCost(int onHand, Double currentCost, int incoming, double incomingCost) {
        if (onHand <= 0 || currentCost == null || incoming + onHand <= 0) {
            return Math.round(incomingCost);
        }
        return Math.round((onHand * currentCost + incoming * incomingCost) / (onHand + incoming));
    }

    private Inventory lockOrCreate(ProductVariant variant) {
        Inventory inventory = inventoryRepository.findByProductVariant_Id(variant.getId()).orElse(null);
        if (inventory == null) {
            inventory = new Inventory();
            inventory.setProductVariant(variant);
            inventory.setQuantity(0);
            inventory.setMinQuantity(0);
            variant.setInventory(inventory);
            return inventory;
        }
        // Khoá rồi đọc lại: dòng có thể đã nạp sẵn (kèm biến thể) từ trước khi một đơn khác trừ kho.
        entityManager.refresh(inventory, LockModeType.PESSIMISTIC_WRITE);
        return inventory;
    }

    // ------------------------------------------------------------------ thẻ kho theo đơn

    /** Ghi thẻ kho cho các dòng vừa trừ kho của đơn mới (gọi sau khi đơn đã có id và mã). */
    public void recordSale(Order order, User actor) {
        if (order.getItems() == null) {
            return;
        }
        for (OrderItem item : order.getItems()) {
            if (Boolean.TRUE.equals(item.getStockReserved())) {
                write(item.getProductVariant(), StockMovementType.SALE, -nz(item.getQuantity()), null,
                        item.getCostPrice(), Ref.order(order), null, actor);
            }
        }
    }

    /**
     * Hoàn kho mọi dòng đã trừ của đơn (huỷ / xoá trước khi giao) — mỗi dòng một lần, theo thứ tự id biến thể để không
     * khoá chéo với đơn khác — và ghi thẻ kho.
     */
    public void releaseOrder(Order order, String note, User actor) {
        if (order.getItems() == null) {
            return;
        }
        List<OrderItem> reserved = order.getItems().stream()
                .filter(i -> Boolean.TRUE.equals(i.getStockReserved()))
                .sorted(Comparator.comparing(i -> i.getProductVariant().getId()))
                .toList();
        for (OrderItem item : reserved) {
            int quantity = nz(item.getQuantity());
            release(item.getProductVariant(), quantity);
            item.setStockReserved(false);
            write(item.getProductVariant(), StockMovementType.RETURN, quantity, null, item.getCostPrice(),
                    Ref.order(order), note, actor);
        }
    }

    /** {@code balanceAfter} null = đọc tồn kho hiện tại (đã gồm thay đổi vừa rồi, cùng giao dịch). */
    private void write(ProductVariant variant, StockMovementType type, int change, Integer balanceAfter,
                       Double unitCost, Ref ref, String note, User actor) {
        Integer balance = balanceAfter;
        if (balance == null && type != StockMovementType.ADJUST) {
            List<InventoryRepository.StockLevel> levels = inventoryRepository.findStockLevels(List.of(variant.getId()));
            balance = levels.isEmpty() ? null : levels.get(0).getQuantity();
        }
        StockMovement movement = new StockMovement();
        movement.setProductVariant(variant);
        movement.setStore(variant.getProduct() == null ? null : variant.getProduct().getStore());
        movement.setType(type);
        movement.setQuantityChange(change);
        movement.setBalanceAfter(balance);
        movement.setUnitCost(unitCost);
        movement.setRefType(ref.type());
        movement.setRefId(ref.id());
        movement.setRefCode(ref.code());
        movement.setNote(note);
        movement.setCreatedBy(actor);
        movementRepository.save(movement);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    static AppException notEnough(ProductVariant variant, int available) {
        String name = "\"" + variant.getProduct().getName() + "\"" + options(variant);
        // Có tên sản phẩm và số lượng cụ thể nên viết sẵn tiếng Việt (app hiển thị nguyên văn).
        String message = available <= 0 ? "Sản phẩm " + name + " đã hết hàng" : "Sản phẩm " + name + " chỉ còn " + available;
        return new AppException(ErrorCode.OUT_OF_STOCK, message);
    }

    /** Tên sản phẩm kèm phân loại, ví dụ "Giày Samba (Trắng / 42)". */
    static String label(ProductVariant variant) {
        return variant.getProduct().getName() + options(variant);
    }

    private static String options(ProductVariant variant) {
        String color = variant.getColor() == null ? "" : variant.getColor().trim();
        String size = variant.getSize() == null ? "" : variant.getSize().trim();
        if (color.isEmpty() && size.isEmpty()) {
            return "";
        }
        return " (" + (color.isEmpty() ? size : size.isEmpty() ? color : color + " / " + size) + ")";
    }
}
