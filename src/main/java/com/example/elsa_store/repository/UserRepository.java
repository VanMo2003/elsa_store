
package com.example.elsa_store.repository;

import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsernameOrEmail(String username, String email);

    Optional<User> findByUsername(String username);

    List<User> findAllByRoleIn(Collection<Role> roles);
}
