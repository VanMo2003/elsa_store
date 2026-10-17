package com.example.elsa_store.specification;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.dto.request.ProductSearchRequest;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tìm kiếm công khai: luôn chỉ hàng đang bán của cửa hàng đã duyệt (giống danh sách công khai), cộng các bộ lọc của
 * người tìm. Không nạp kèm (fetch) danh sách biến thể — nạp kèm collection cùng phân trang làm Hibernate phải phân trang
 * trong bộ nhớ và hỏng câu đếm tổng.
 */
public final class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> search(ProductSearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Chỉ hàng đang bán của cửa hàng đã duyệt (hoặc dữ liệu cũ chưa gắn cửa hàng).
            predicates.add(cb.or(cb.isNull(root.get("status")), cb.equal(root.get("status"), 1)));
            Join<Product, Store> store = root.join("store", JoinType.LEFT);
            predicates.add(cb.or(
                    cb.isNull(store.get("id")),
                    cb.isNull(store.get("approvalStatus")),
                    cb.equal(store.get("approvalStatus"), ApprovalStatus.APPROVED)));
            // Cửa hàng bị ADMIN đình chỉ: ẩn khỏi tìm kiếm.
            predicates.add(cb.or(
                    cb.isNull(store.get("id")),
                    cb.isNull(store.get("suspended")),
                    cb.isFalse(store.get("suspended"))));
            Join<Product, Category> category = root.join("category", JoinType.LEFT);

            String keyword = req.getKeyword() == null ? "" : req.getKeyword().trim().toLowerCase(Locale.ROOT);
            if (!keyword.isEmpty()) {
                // Collation mặc định của MySQL 8 không phân biệt dấu: "giay" khớp "Giày".
                String pattern = "%" + escapeLike(keyword) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(category.get("name")), pattern, '\\')));
            }
            if (req.getCategoryId() != null) {
                // Danh mục gốc gồm cả danh mục con của nó.
                predicates.add(cb.or(
                        cb.equal(category.get("id"), req.getCategoryId()),
                        cb.equal(category.join("parent", JoinType.LEFT).get("id"), req.getCategoryId())));
            }
            if (req.getStoreId() != null) {
                predicates.add(cb.equal(store.get("id"), req.getStoreId()));
            }
            if (req.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("basePrice"), req.getMinPrice()));
            }
            if (req.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("basePrice"), req.getMaxPrice()));
            }
            String color = blankToNull(req.getColor());
            String size = blankToNull(req.getSize());
            if (color != null || size != null) {
                // Có ít nhất một biến thể khớp màu / size (không nhân bản dòng sản phẩm như khi join).
                Subquery<Long> variants = query.subquery(Long.class);
                Root<ProductVariant> v = variants.from(ProductVariant.class);
                List<Predicate> match = new ArrayList<>();
                match.add(cb.equal(v.get("product"), root));
                if (color != null) {
                    match.add(cb.equal(cb.lower(v.get("color")), color.toLowerCase(Locale.ROOT)));
                }
                if (size != null) {
                    match.add(cb.equal(cb.lower(v.get("size")), size.toLowerCase(Locale.ROOT)));
                }
                variants.select(v.get("id")).where(match.toArray(Predicate[]::new));
                predicates.add(cb.exists(variants));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Ký tự đại diện của LIKE trong từ khoá được tìm đúng nghĩa đen ("50%" không khớp mọi thứ). */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
