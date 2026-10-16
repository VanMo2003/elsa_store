
package com.example.elsa_store.entity;

import com.example.elsa_store.constant.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity {

    @Column(name = "username", length = 50, nullable = false, unique = true)
    private String username;

    @JsonIgnore
    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "email", length = 100, unique = true)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30)
    private Role role;

    /** Ảnh đại diện (đường dẫn /uploads/avatars/...); null = hiện chữ cái đầu của tên. */
    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    /** Cửa hàng mà quản lý / nhân viên làm việc. Chủ cửa hàng gắn qua {@code Store.owner}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    // Builder.Default: nếu không, User.builder() bỏ qua giá trị khởi tạo và tạo ra tài khoản bị khoá.
    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * Phiên bản token: mỗi lần đổi mật khẩu hoặc bị khoá thì tăng lên, mọi token mang số cũ bị từ chối — đổi mật khẩu là
     * đăng xuất mọi thiết bị khác. Dùng số thay cho mốc thời gian để không có khe hở "cùng một giây". Trống = 0.
     */
    @Column(name = "token_version")
    private Integer tokenVersion;

    public int currentTokenVersion() {
        return tokenVersion == null ? 0 : tokenVersion;
    }

    /** Thông tin đăng nhập vừa đổi: mọi token đã cấp hết hiệu lực. */
    public void markCredentialsChanged() {
        this.tokenVersion = currentTokenVersion() + 1;
    }
}
