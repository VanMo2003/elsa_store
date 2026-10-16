package com.example.elsa_store.constant;

/** Banner trang chủ chạm vào thì mở đâu. */
public enum BannerLinkType {
    NONE,
    /** {@code linkValue} = id sản phẩm. */
    PRODUCT,
    /** {@code linkValue} = id cửa hàng. */
    STORE,
    /** {@code linkValue} = id danh mục (mở tìm kiếm lọc danh mục). */
    CATEGORY,
    /** {@code linkValue} = từ khoá tìm kiếm. */
    SEARCH
}
