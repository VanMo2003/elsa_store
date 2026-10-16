
package com.example.elsa_store.dto.response;

import com.example.elsa_store.constant.Role;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String phone;
    private String fullName;
    /** Ảnh đại diện; null = chưa có. */
    private String avatarUrl;
    private Boolean enabled;
    private Role role;
    private Long storeId;
    private String storeName;

    public Long getId() { return id; }

    public String getAvatarUrl() { return avatarUrl; }

    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }

    public void setPhone(String phone) { this.phone = phone; }

    public String getFullName() { return fullName; }

    public void setFullName(String fullName) { this.fullName = fullName; }

    public Boolean getEnabled() { return enabled; }

    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Role getRole() { return role; }

    public void setRole(Role role) { this.role = role; }

    public Long getStoreId() { return storeId; }

    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public String getStoreName() { return storeName; }

    public void setStoreName(String storeName) { this.storeName = storeName; }
}
