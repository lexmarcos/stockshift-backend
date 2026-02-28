package br.com.stockshift.security;

import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrincipalTest {

    @Test
    void createShouldIncludeRoleAndPermissionAuthorities() {
        Permission permission = new Permission();
        permission.setCode("transfers:validate");

        Role role = new Role();
        role.setName("OPERATOR");
        role.setPermissions(new HashSet<>(Set.of(permission)));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        user.setEmail("operator@test.com");
        user.setPassword("encoded-password");
        user.setIsActive(true);
        user.setRoles(new HashSet<>(Set.of(role)));
        user.setWarehouses(new HashSet<>());

        UserPrincipal principal = UserPrincipal.create(user);

        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_OPERATOR", "transfers:validate");
        assertThat(principal.isHasFullAccess()).isFalse();
    }

    @Test
    void createShouldSetFullAccessForAdminRole() {
        Role role = new Role();
        role.setName("ADMIN");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        user.setEmail("admin@test.com");
        user.setPassword("encoded-password");
        user.setIsActive(true);
        user.setRoles(new HashSet<>(Set.of(role)));
        user.setWarehouses(new HashSet<>());

        UserPrincipal principal = UserPrincipal.create(user);

        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
        assertThat(principal.isHasFullAccess()).isTrue();
    }
}
