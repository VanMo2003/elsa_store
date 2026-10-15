
package com.example.elsa_store.mapper;

import com.example.elsa_store.dto.request.UserRequest;
import com.example.elsa_store.dto.response.UserResponse;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;

public class UserMapper {

    public static User toEntity(UserRequest req) {
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(req.getPassword());
        u.setEmail(req.getEmail());
        u.setPhone(req.getPhone());
        u.setFullName(req.getFullName());
        u.setRole(req.getRole());
        u.setEnabled(req.getEnabled() != null ? req.getEnabled() : Boolean.TRUE);
        return u;
    }

    /** Cập nhật thông tin hồ sơ; mật khẩu, vai trò và trạng thái do service quyết định. */
    public static void update(User u, UserRequest req) {
        if (req.getEmail() != null) {
            u.setEmail(req.getEmail());
        }
        if (req.getPhone() != null) {
            u.setPhone(req.getPhone());
        }
        if (req.getFullName() != null) {
            u.setFullName(req.getFullName());
        }
    }

    public static UserResponse toResponse(User u) {
        return toResponse(u, null);
    }

    /** {@code store}: cửa hàng của người dùng (chủ → cửa hàng sở hữu; quản lý/nhân viên → nơi làm việc). */
    public static UserResponse toResponse(User u, Store store) {
        UserResponse res = new UserResponse();
        res.setId(u.getId());
        res.setUsername(u.getUsername());
        res.setEmail(u.getEmail());
        res.setPhone(u.getPhone());
        res.setFullName(u.getFullName());
        res.setEnabled(u.isEnabled());
        res.setRole(u.getRole());
        Store resolved = store != null ? store : u.getStore();
        if (resolved != null) {
            res.setStoreId(resolved.getId());
            res.setStoreName(resolved.getName());
        }
        return res;
    }
}
