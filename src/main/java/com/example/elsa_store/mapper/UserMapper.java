
package com.example.elsa_store.mapper;

import com.example.elsa_store.dto.request.UserRequest;
import com.example.elsa_store.dto.response.UserResponse;
import com.example.elsa_store.entity.User;
import org.mapstruct.Mapper;

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

    public static void update(User u, UserRequest req) {
        if (req.getUsername() != null) {
            u.setUsername(req.getUsername());
        }
        if (req.getPassword() != null) {
            u.setPassword(req.getPassword());
        }
        if (req.getEmail() != null) {
            u.setEmail(req.getEmail());
        }
        if (req.getPhone() != null) {
            u.setPhone(req.getPhone());
        }
        if (req.getFullName() != null) {
            u.setFullName(req.getFullName());
        }
        if (req.getEnabled() != null) {
            u.setEnabled(req.getEnabled());
        }
    }

    public static UserResponse toResponse(User u) {
        UserResponse res = new UserResponse();
        res.setId(u.getId());
        res.setUsername(u.getUsername());
        res.setEmail(u.getEmail());
        res.setPhone(u.getPhone());
        res.setFullName(u.getFullName());
        res.setEnabled(u.isEnabled());
        return res;
    }
}
