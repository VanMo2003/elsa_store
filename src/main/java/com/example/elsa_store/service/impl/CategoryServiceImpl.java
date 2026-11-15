package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.request.CategoryRequest;
import com.example.elsa_store.dto.response.CategoryResponse;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.CategoryMapper;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Danh mục sản phẩm dùng chung cho mọi cửa hàng (ADMIN quản lý), đúng <b>2 tầng</b> qua cột {@code categories.parent_id}:
 * {@code parent_id} trống = danh mục tầng 1 (vd. "Áo sơ mi"), có giá trị = id danh mục cha tầng 1 (vd. "Sơ mi ngắn tay").
 * <ul>
 *   <li>Danh mục cha phải là tầng 1 (không có tầng 3), không làm cha của chính nó.</li>
 *   <li>Danh mục tầng 1 đang có danh mục con thì không chuyển thành tầng 2.</li>
 *   <li>Tên không trùng với danh mục cùng cấp (cùng cha), không phân biệt hoa thường.</li>
 *   <li>Không xoá danh mục còn danh mục con hoặc còn sản phẩm (tránh xoá dây chuyền / sản phẩm mất danh mục).</li>
 * </ul>
 * Danh sách trả theo dạng cây phẳng: mỗi danh mục tầng 1 (A→Z) rồi ngay sau là các danh mục con của nó (A→Z).
 */
@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    static final int MAX_NAME = 100;
    private static final Collator VI = Collator.getInstance(Locale.forLanguageTag("vi-VN"));

    private final CategoryRepository categoryRepository;
    private final AccessGuard accessGuard;
    private final ProductRepository productRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository, AccessGuard accessGuard,
                               ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.accessGuard = accessGuard;
        this.productRepository = productRepository;
    }

    @Override
    public CategoryResponse create(CategoryRequest req) {
        // Danh mục dùng chung cho mọi cửa hàng nên do ADMIN quản lý.
        accessGuard.requireAdmin();
        String name = cleanName(req.getName());
        Category parent = parentOf(req.getParentId(), null);
        requireUniqueName(name, parent, null);
        Category c = Category.builder().name(name).parent(parent).build();
        c = categoryRepository.save(c);
        return toResponse(c, Map.of());
    }

    @Override
    public CategoryResponse update(Long id, CategoryRequest req) {
        accessGuard.requireAdmin();
        Category c = find(id);
        String name = cleanName(req.getName());
        Category parent = parentOf(req.getParentId(), c);
        if (parent != null && c.getChildren() != null && !c.getChildren().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Category has subcategories so it must stay top-level");
        }
        requireUniqueName(name, parent, c.getId());
        c.setName(name);
        c.setParent(parent);
        c = categoryRepository.save(c);
        return toResponse(c, productCounts());
    }

    @Override
    public void delete(Long id) {
        accessGuard.requireAdmin();
        Category c = find(id);
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
    public List<CategoryResponse> getAll() {
        Map<Long, Long> counts = productCounts();
        List<Category> all = categoryRepository.findAll();
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

    // ------------------------------------------------------------------ nội bộ

    private Category find(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    /** Danh mục cha hợp lệ: tồn tại, là tầng 1, không phải chính {@code self}; null = tầng 1. */
    private Category parentOf(Long parentId, Category self) {
        if (parentId == null) {
            return null;
        }
        if (self != null && parentId.equals(self.getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "parentId: a category cannot be its own parent");
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
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

    /** Không trùng tên (không phân biệt hoa thường) với danh mục cùng cha. */
    private void requireUniqueName(String name, Category parent, Long selfId) {
        Long parentId = parent == null ? null : parent.getId();
        boolean taken = categoryRepository.findAll().stream()
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

    private static CategoryResponse toResponse(Category c, Map<Long, Long> productCounts) {
        CategoryResponse res = CategoryMapper.toResponse(c);
        res.setLevel(c.getParent() == null ? 1 : 2);
        res.setChildCount(c.getChildren() == null ? 0 : c.getChildren().size());
        res.setProductCount(c.getId() == null ? 0L : productCounts.getOrDefault(c.getId(), 0L));
        return res;
    }
}
