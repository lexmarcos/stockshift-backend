package br.com.stockshift.security;

import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.Warehouse;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private UUID id;
    private UUID tenantId;
    private String email;
    private String password;
    private boolean active;
    private Collection<? extends GrantedAuthority> authorities;
    private Set<UUID> warehouseIds;
    private boolean hasFullAccess;

    public static UserPrincipal create(User user) {
        Set<String> authorityNames = new LinkedHashSet<>();
        user.getRoles().forEach(role -> {
            authorityNames.add("ROLE_" + role.getName());
            if (role.getPermissions() != null) {
                role.getPermissions().forEach(permission -> {
                    String code = resolvePermissionCode(permission);
                    if (code != null && !code.isBlank()) {
                        authorityNames.add(code);
                    }
                });
            }
        });

        Collection<GrantedAuthority> authorities = authorityNames.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));

        Set<UUID> warehouseIds = user.getWarehouses().stream()
                .map(warehouse -> warehouse.getId())
                .collect(Collectors.toSet());

        return new UserPrincipal(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getPassword(),
                user.getIsActive(),
                authorities,
                warehouseIds,
                isAdmin
        );
    }

    private static String resolvePermissionCode(br.com.stockshift.model.entity.Permission permission) {
        if (permission.getCode() != null && !permission.getCode().isBlank()) {
            return permission.getCode().toLowerCase(Locale.ROOT);
        }

        if (permission.getResource() == null || permission.getAction() == null) {
            return "";
        }

        return (permission.getResource() + ":" + permission.getAction()).toLowerCase(Locale.ROOT);
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
