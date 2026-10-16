
package com.example.elsa_store.repository;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    /** Khoá dòng tài khoản (SELECT … FOR UPDATE) trong lúc ghi sổ xu. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") Long id);

    Optional<User> findFirstByEmailIgnoreCase(String email);

    List<User> findAllByRoleIn(Collection<Role> roles);

    List<User> findAllByRole(Role role);

    Page<User> findAllByRole(Role role, Pageable pageable);

    /** ADMIN tìm tài khoản: theo vai trò và / hoặc mẫu {@code like} (đã viết thường) trên tên đăng nhập, họ tên, email, SĐT. */
    @Query("""
        select u from User u
        where (:role is null or u.role = :role)
          and (:pattern is null
               or lower(u.username) like :pattern
               or lower(u.fullName) like :pattern
               or lower(u.email) like :pattern
               or u.phone like :pattern)
    """)
    Page<User> search(@Param("role") Role role, @Param("pattern") String pattern, Pageable pageable);

    /** Quản lý và nhân viên của một cửa hàng. */
    List<User> findAllByStore_Id(Long storeId);

    /** Đủ để quyết định một token còn dùng được không, mà không nạp cả tài khoản (chạy ở mọi request). */
    @Query("select u.enabled as enabled, u.tokenVersion as tokenVersion from User u where u.username = :username")
    Optional<AuthState> findAuthState(@Param("username") String username);

    interface AuthState {
        Boolean getEnabled();

        Integer getTokenVersion();
    }
}
