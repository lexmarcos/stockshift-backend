package br.com.stockshift.service;

import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.repository.UserRoleWarehouseRepository;
import br.com.stockshift.security.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionResolverService {

    private final UserRoleWarehouseRepository userRoleWarehouseRepository;

    @Transactional(readOnly = true)
    public Set<String> resolveUserPermissions(UUID userId, UUID warehouseId) {
        if (userId == null || warehouseId == null) {
            return Set.of();
        }

        Set<Role> roles = userRoleWarehouseRepository.findRolesByUserIdAndWarehouseId(userId, warehouseId);
        if (roles.isEmpty()) {
            return Set.of();
        }

        boolean isAdmin = roles.stream().anyMatch(this::isAdminRole);
        if (isAdmin) {
            return Set.copyOf(PermissionCodes.all());
        }

        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(this::resolvePermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<String> resolveUserRoleNames(UUID userId, UUID warehouseId) {
        if (userId == null || warehouseId == null) {
            return Set.of();
        }

        return userRoleWarehouseRepository.findRolesByUserIdAndWarehouseId(userId, warehouseId).stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    private boolean isAdminRole(Role role) {
        if (role == null || role.getName() == null) {
            return false;
        }

        String normalized = role.getName().toUpperCase(Locale.ROOT);
        return "ADMIN".equals(normalized) || "SUPER_ADMIN".equals(normalized);
    }

    private String resolvePermissionCode(Permission permission) {
        if (permission == null) {
            return null;
        }

        if (permission.getCode() != null && !permission.getCode().isBlank()) {
            return permission.getCode().toLowerCase(Locale.ROOT);
        }

        if (permission.getResource() == null || permission.getAction() == null) {
            return null;
        }

        String resource = permission.getResource().toLowerCase(Locale.ROOT);
        String action = permission.getAction().toLowerCase(Locale.ROOT);

        return switch (resource) {
            case "user" -> "users:" + action;
            case "warehouse" -> "warehouses:" + action;
            case "product" -> "products:" + action;
            case "report" -> "reports:" + action;
            case "stock" -> "batches:" + action;
            case "transfer" -> switch (action) {
                case "cancel" -> PermissionCodes.TRANSFERS_DELETE;
                default -> "transfers:" + action;
            };
            default -> resource + ":" + action;
        };
    }
}
