package com.example.elsa_store.security;

import com.example.elsa_store.constant.ApprovalStatus;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.security.AccessGuard.Access;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ma trận quyền theo vai trò và theo cửa hàng — nền của toàn bộ phân quyền phía BE. */
class AccessGuardTest {

    private final UserRepository users = mock(UserRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final AccessGuard guard = new AccessGuard(users, stores);

    private final Store storeA = store(1L, ApprovalStatus.APPROVED);
    private final Store storeB = store(2L, ApprovalStatus.APPROVED);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Store store(long id, ApprovalStatus status) {
        Store store = Store.builder().name("Store " + id).approvalStatus(status).build();
        store.setId(id);
        return store;
    }

    private User signIn(String username, Role role, Store store) {
        User user = User.builder().username(username).role(role).store(store).enabled(true).build();
        user.setId((long) username.hashCode());
        when(users.findByUsername(username)).thenReturn(Optional.of(user));
        if (role == Role.STORE_OWNER) {
            when(stores.findByOwner_Id(user.getId())).thenReturn(Optional.ofNullable(store));
        }
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        username, "n/a", AuthorityUtils.createAuthorityList("ROLE_" + role)));
        return user;
    }

    private static ErrorCode codeOf(AppException e) {
        return e.getErrorCode();
    }

    @Test
    void anonymousVisitorsAreNotAuthenticated() {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertEquals(Optional.empty(), guard.currentUserOrEmpty());
        assertEquals(ErrorCode.UN_AUTHENTICATED, codeOf(assertThrows(AppException.class, guard::currentUser)));
    }

    @Test
    void aLockedAccountLosesItsTokenImmediately() {
        User locked = signIn("locked01", Role.STAFF, storeA);
        locked.setEnabled(false);

        assertEquals(ErrorCode.UN_AUTHENTICATED, codeOf(assertThrows(AppException.class, guard::currentUser)));
    }

    @Test
    void ownerManagerAndStaffCanReadAndWriteTheirOwnStore() {
        for (Role role : new Role[] {Role.STORE_OWNER, Role.STORE_MANAGER, Role.STAFF}) {
            signIn("member-" + role, role, storeA);
            assertSame(role, guard.authorize(storeA, Access.READ).getRole());
            assertSame(role, guard.authorize(storeA, Access.STAFF_WRITE).getRole());
        }
    }

    @Test
    void staffCannotDoManagerLevelWork() {
        signIn("staff01", Role.STAFF, storeA);

        assertEquals(ErrorCode.UN_AUTHORIZED,
                codeOf(assertThrows(AppException.class, () -> guard.authorize(storeA, Access.MANAGER_WRITE))));
        assertEquals(ErrorCode.UN_AUTHORIZED,
                codeOf(assertThrows(AppException.class, () -> guard.authorize(storeA, Access.MANAGER_READ))));
        assertEquals(ErrorCode.UN_AUTHORIZED, codeOf(assertThrows(AppException.class, guard::requireStoreManager)));
    }

    @Test
    void ownerAndManagerCanDoManagerLevelWork() {
        for (Role role : new Role[] {Role.STORE_OWNER, Role.STORE_MANAGER}) {
            signIn("boss-" + role, role, storeA);
            guard.authorize(storeA, Access.MANAGER_WRITE);
            assertEquals(storeA.getId(), guard.requireStoreManager().getId());
        }
    }

    @Test
    void nobodyReachesAnotherStore() {
        for (Role role : new Role[] {Role.STORE_OWNER, Role.STORE_MANAGER, Role.STAFF}) {
            signIn("other-" + role, role, storeA);
            assertEquals(ErrorCode.UN_AUTHORIZED,
                    codeOf(assertThrows(AppException.class, () -> guard.authorize(storeB, Access.READ))));
            assertEquals(ErrorCode.UN_AUTHORIZED,
                    codeOf(assertThrows(AppException.class, () -> guard.authorize((Store) null, Access.READ))));
        }
    }

    @Test
    void adminOnlyReadsStoreData() {
        signIn("admin01", Role.ADMIN, null);

        guard.authorize(storeA, Access.READ);
        guard.authorize(storeB, Access.MANAGER_READ);
        for (Access write : new Access[] {Access.STAFF_WRITE, Access.MANAGER_WRITE}) {
            assertEquals(ErrorCode.ADMIN_READ_ONLY,
                    codeOf(assertThrows(AppException.class, () -> guard.authorize(storeA, write))));
        }
        assertEquals(ErrorCode.ADMIN_READ_ONLY, codeOf(assertThrows(AppException.class, guard::requireStoreMember)));
    }

    @Test
    void customersHaveNoStoreAccessAtAll() {
        signIn("user001", Role.USER, null);

        assertEquals(ErrorCode.UN_AUTHORIZED,
                codeOf(assertThrows(AppException.class, () -> guard.authorize(storeA, Access.READ))));
        assertEquals(ErrorCode.UN_AUTHORIZED, codeOf(assertThrows(AppException.class, guard::requireStoreMember)));
        assertEquals(ErrorCode.UN_AUTHORIZED, codeOf(assertThrows(AppException.class, guard::requireAdmin)));
    }

    @Test
    void anOwnerWaitingForApprovalCannotRunTheStore() {
        Store pending = store(3L, ApprovalStatus.PENDING);
        signIn("pending01", Role.STORE_OWNER, pending);

        // Vẫn xem được hồ sơ của mình để biết trạng thái và gửi lại…
        assertEquals(pending.getId(), guard.requireOwnStore().getId());
        // …nhưng mọi thao tác vận hành cửa hàng đều bị khoá cho tới khi ADMIN duyệt.
        assertEquals(ErrorCode.STORE_NOT_APPROVED,
                codeOf(assertThrows(AppException.class, guard::requireApprovedOwner)));
        assertEquals(ErrorCode.STORE_NOT_APPROVED,
                codeOf(assertThrows(AppException.class, guard::requireStoreMember)));
        assertEquals(ErrorCode.STORE_NOT_APPROVED,
                codeOf(assertThrows(AppException.class, () -> guard.authorize(pending, Access.READ))));
    }

    @Test
    void storeStaffWithoutAStoreAreRejected() {
        signIn("orphan01", Role.STAFF, null);

        assertEquals(ErrorCode.STORE_REQUIRED, codeOf(assertThrows(AppException.class, guard::requireStoreMember)));
    }
}
