package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.CategoryRequest;
import com.example.elsa_store.dto.response.CategoryResponse;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.CategoryMapper;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Danh mục sản phẩm <b>riêng của từng cửa hàng</b> ({@code categories.store_id}), đúng <b>2 tầng</b> qua
 * {@code categories.parent_id}: trống = danh mục cha (tầng 1), có giá trị = id danh mục cha tầng 1 của cùng cửa hàng.
 * <ul>
 *   <li>Chủ / quản lý thêm, sửa, xoá danh mục của cửa hàng mình; nhân viên chỉ đọc; ADMIN chỉ xem.</li>
 *   <li>Danh mục cha phải là tầng 1 của cùng cửa hàng; không tầng 3, không tự làm cha; cha đang có con không xuống tầng 2.</li>
 *   <li>Tên không trùng với danh mục cùng cấp trong cửa hàng (không phân biệt hoa thường).</li>
 *   <li>Không xoá danh mục còn danh mục con hoặc còn sản phẩm.</li>
 * </ul>
 * {@code GET /categories}: có {@code storeId} → danh mục của cửa hàng đó; người của cửa hàng → danh mục của mình; ADMIN →
 * mọi danh mục kèm tên cửa hàng; khách → danh sách <b>gộp theo tên</b> của các cửa hàng đang hiện (một "Áo sơ mi" cho cả
 * sàn), lọc sản phẩm theo danh mục gộp sẽ khớp theo tên ({@code ProductRepository.PUBLIC_PRODUCTS}).
 */
@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    static final int MAX_NAME = 100;
    private static final Collator VI = Collator.getInstance(Locale.forLanguageTag("vi-VN"));
    private static final Locale VI_LOCALE = Locale.forLanguageTag("vi-VN");

    private final CategoryRepository categoryRepository;
    private final AccessGuard accessGuard;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository, AccessGuard accessGuard,
                               ProductRepository productRepository, StoreRepository storeRepository) {
        this.categoryRepository = categoryRepository;
        this.accessGuard = accessGuard;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
    }

    @Override
    public CategoryResponse create(CategoryRequest req) {
        Store store = accessGuard.requireStoreManager();
        String name = cleanName(req.getName());
        Category parent = parentOf(req.getParentId(), null, store);
        requireUniqueName(name, parent, null, store);
        Category c = Category.builder().name(name).parent(parent).store(store).build();
        c = categoryRepository.save(c);
        return toResponse(c, Map.of());
    }

    @Override
    public CategoryResponse update(Long id, CategoryRequest req) {
        Store store = accessGuard.requireStoreManager();
        Category c = own(id, store);
        String name = cleanName(req.getName());
        Category parent = parentOf(req.getParentId(), c, store);
        if (parent != null && c.getChildren() != null && !c.getChildren().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Category has subcategories so it must stay top-level");
        }
        requireUniqueName(name, parent, c.getId(), store);
        c.setName(name);
        c.setParent(parent);
        c = categoryRepository.save(c);
        return toResponse(c, productCounts());
    }

    @Override
    public void delete(Long id) {
        Store store = accessGuard.requireStoreManager();
        Category c = own(id, store);
        if (c.getChildren() != null && !c.getChildren().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Delete or move its subcategories first");
        }
        long products = productRepository.countByCategory_Id(id);
        if (products > 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Category is used by " + products + " products");
        }
        categoryRepository.delete(c);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return toResponse(find(id), productCounts());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll(Long storeId) {
        if (storeId != null) {
            if (!storeRepository.existsById(storeId)) {
                throw new ResourceNotFoundException("Store not found with id=" + storeId);
            }
            return tree(categoryRepository.findAllByStore_Id(storeId), productCounts());
        }
        User current = accessGuard.currentUserOrEmpty().orElse(null);
        if (current != null && current.getRole().isStoreRole()) {
            Store mine = accessGuard.storeOf(current);
            return mine == null ? List.of() : tree(categoryRepository.findAllByStore_Id(mine.getId()), productCounts());
        }
        if (current != null && current.getRole() == Role.ADMIN) {
            return tree(categoryRepository.findAll().stream().filter(c -> c.getStore() != null).toList(), productCounts());
        }
        return browse(categoryRepository.findAllOfVisibleStores(), productCounts());
    }

    // ------------------------------------------------------------------ nội bộ

    private Category find(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    /** Danh mục của chính cửa hàng; của cửa hàng khác thì báo không có quyền. */
    private Category own(Long id, Store store) {
        Category c = find(id);
        if (c.getStore() == null || !Objects.equals(c.getStore().getId(), store.getId())) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        return c;
    }

    /** Danh mục cha hợp lệ: cùng cửa hàng, là tầng 1, không phải chính {@code self}; null = tầng 1. */
    private Category parentOf(Long parentId, Category self, Store store) {
        if (parentId == null) {
            return null;
        }
        if (self != null && parentId.equals(self.getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "parentId: a category cannot be its own parent");
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
        if (parent.getStore() == null || !Objects.equals(parent.getStore().getId(), store.getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "parentId: must be a category of your store");
        }
        if (parent.getParent() != null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "parentId: must be a top-level category");
        }
        return parent;
    }

    static String cleanName(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "name: must not be blank");
        }
        if (name.length() > MAX_NAME) {
            throw new AppException(ErrorCode.BAD_REQUEST, "name: must be at most 100 characters");
        }
        return name;
    }

    /** Không trùng tên (không phân biệt hoa thường) với danh mục cùng cha trong cùng cửa hàng. */
    private void requireUniqueName(String name, Category parent, Long selfId, Store store) {
        Long parentId = parent == null ? null : parent.getId();
        boolean taken = categoryRepository.findAllByStore_Id(store.getId()).stream()
                .filter(c -> !Objects.equals(c.getId(), selfId))
                .filter(c -> Objects.equals(c.getParent() == null ? null : c.getParent().getId(), parentId))
                .anyMatch(c -> c.getName() != null && c.getName().equalsIgnoreCase(name));
        if (taken) {
            throw new AppException(ErrorCode.BAD_REQUEST, "name: already exists at this level");
        }
    }

    private Map<Long, Long> productCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : productRepository.countByCategory()) {
            if (row[0] != null) {
                counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }
        return counts;
    }

    /** Cây phẳng: mỗi danh mục cha (A→Z) rồi ngay sau là các con (A→Z). */
    static List<CategoryResponse> tree(List<Category> all, Map<Long, Long> counts) {
        Comparator<Category> byName = Comparator.comparing(Category::getName, VI);
        List<CategoryResponse> out = new ArrayList<>();
        all.stream().filter(c -> c.getParent() == null).sorted(byName).forEach(root -> {
            out.add(toResponse(root, counts));
            all.stream()
                    .filter(c -> c.getParent() != null && Objects.equals(c.getParent().getId(), root.getId()))
                    .sorted(byName)
                    .forEach(child -> out.add(toResponse(child, counts)));
        });
        return out;
    }

    /**
     * Danh sách cho khách: gộp danh mục cùng tên của các cửa hàng (không phân biệt hoa thường) — mỗi nhóm lấy danh mục có id
     * nhỏ nhất làm đại diện, cộng dồn số sản phẩm; con gộp theo (tên cha, tên con).
     */
    static List<CategoryResponse> browse(List<Category> all, Map<Long, Long> counts) {
        Map<String, List<Category>> roots = new LinkedHashMap<>();
        Map<String, Map<String, List<Category>>> children = new LinkedHashMap<>();
        for (Category c : all) {
            if (c.getParent() == null) {
                roots.computeIfAbsent(key(c.getName()), k -> new ArrayList<>()).add(c);
            } else {
                children.computeIfAbsent(key(c.getParent().getName()), k -> new LinkedHashMap<>())
                        .computeIfAbsent(key(c.getName()), k -> new ArrayList<>()).add(c);
            }
        }
        List<CategoryResponse> out = new ArrayList<>();
        roots.values().stream()
                .map(group -> merged(group, counts, null))
                .sorted(Comparator.comparing(CategoryResponse::getName, VI))
                .forEach(root -> {
                    out.add(root);
                    children.getOrDefault(key(root.getName()), Map.of()).values().stream()
                            .map(group -> merged(group, counts, root))
                            .sorted(Comparator.comparing(CategoryResponse::getName, VI))
                            .forEach(out::add);
                });
        return out;
    }

    private static CategoryResponse merged(List<Category> group, Map<Long, Long> counts, CategoryResponse parent) {
        Category rep = group.stream().min(Comparator.comparing(Category::getId)).orElseThrow();
        CategoryResponse res = new CategoryResponse();
        res.setId(rep.getId());
        res.setName(rep.getName());
        res.setLevel(parent == null ? 1 : 2);
        if (parent != null) {
            res.setParentId(parent.getId());
            res.setParentName(parent.getName());
        }
        res.setChildCount((int) group.stream().mapToLong(c -> c.getChildren() == null ? 0 : c.getChildren().size()).sum());
        res.setProductCount(group.stream().mapToLong(c -> counts.getOrDefault(c.getId(), 0L)).sum());
        return res;
    }

    private static String key(String name) {
        return name == null ? "" : name.strip().toLowerCase(VI_LOCALE);
    }

    private static CategoryResponse toResponse(Category c, Map<Long, Long> productCounts) {
        CategoryResponse res = CategoryMapper.toResponse(c);
        res.setLevel(c.getParent() == null ? 1 : 2);
        res.setChildCount(c.getChildren() == null ? 0 : c.getChildren().size());
        res.setProductCount(c.getId() == null ? 0L : productCounts.getOrDefault(c.getId(), 0L));
        if (c.getStore() != null) {
            res.setStoreId(c.getStore().getId());
            res.setStoreName(c.getStore().getName());
        }
        return res;
    }
}
