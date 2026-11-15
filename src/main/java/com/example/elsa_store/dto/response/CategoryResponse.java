
package com.example.elsa_store.dto.response;

public class CategoryResponse {

    private Long id;
    private String name;
    private Long parentId;
    private String parentName;
    /** 1 = danh mục cha (parent_id trống), 2 = danh mục con. */
    private Integer level;
    /** Số danh mục con (chỉ tầng 1 có). */
    private Integer childCount;
    /** Số sản phẩm gắn trực tiếp vào danh mục này. */
    private Long productCount;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public Long getParentId() { return parentId; }

    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getParentName() { return parentName; }

    public void setParentName(String parentName) { this.parentName = parentName; }

    public Integer getLevel() { return level; }

    public void setLevel(Integer level) { this.level = level; }

    public Integer getChildCount() { return childCount; }

    public void setChildCount(Integer childCount) { this.childCount = childCount; }

    public Long getProductCount() { return productCount; }

    public void setProductCount(Long productCount) { this.productCount = productCount; }
}
