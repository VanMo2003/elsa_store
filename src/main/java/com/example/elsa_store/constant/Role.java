package com.example.elsa_store.constant;

/**
 * Vai trò của tài khoản.
 * <ul>
 *   <li>{@code ADMIN} — xét duyệt cửa hàng, quản lý tài khoản và danh mục; chỉ xem dữ liệu của cửa hàng.</li>
 *   <li>{@code STORE_OWNER} — chủ cửa hàng (đăng ký cửa hàng và chờ ADMIN duyệt), tạo quản lý và nhân viên.</li>
 *   <li>{@code STORE_MANAGER} — quản lý cửa hàng do chủ tạo, đủ quyền vận hành cửa hàng.</li>
 *   <li>{@code STAFF} — nhân viên: khách hàng, tạo đơn, quản lý sản phẩm nhưng không được xoá.</li>
 *   <li>{@code USER} — khách mua hàng (tự đăng ký).</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    STORE_OWNER,
    STORE_MANAGER,
    STAFF,
    USER;

    /** Vai trò làm việc trong một cửa hàng. */
    public boolean isStoreRole() {
        return this == STORE_OWNER || this == STORE_MANAGER || this == STAFF;
    }

    /** Chủ hoặc quản lý cửa hàng: xoá dữ liệu, xem báo cáo, quản lý nhân sự. */
    public boolean isStoreManager() {
        return this == STORE_OWNER || this == STORE_MANAGER;
    }
}
