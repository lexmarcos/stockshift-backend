package br.com.stockshift.service;

import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.security.UserPrincipal;
import br.com.stockshift.security.WarehouseContext;
import br.com.stockshift.repository.UserRoleWarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseAccessService {

    private final UserRoleWarehouseRepository userRoleWarehouseRepository;

    public boolean canAccessWarehouse(UUID userId, UUID warehouseId) {
        if (userId == null || warehouseId == null) {
            return false;
        }
        return userRoleWarehouseRepository.existsByUserIdAndWarehouseId(userId, warehouseId);
    }

    public Set<UUID> getUserWarehouseIds() {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return Set.of();
        }
        return userRoleWarehouseRepository.findWarehouseIdsByUserId(userId);
    }

    public boolean hasFullAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority) || "ROLE_SUPER_ADMIN".equals(authority));
    }

    public UUID getTenantId() {
        UserPrincipal principal = getCurrentPrincipalIfAvailable();
        if (principal != null) {
            return principal.getTenantId();
        }
        return TenantContext.getTenantId();
    }

    public void validateWarehouseAccess(UUID warehouseId) {
        if (warehouseId == null) {
            throw new ForbiddenException("Warehouse is required");
        }

        UUID currentWarehouseId = WarehouseContext.getWarehouseId();
        if (currentWarehouseId != null && !currentWarehouseId.equals(warehouseId)) {
            throw new ForbiddenException("Requested warehouse is outside current token scope");
        }

        if (hasFullAccess()) {
            return;
        }

        UUID userId = getCurrentUserId();
        if (!canAccessWarehouse(userId, warehouseId)) {
            log.warn("User {} attempted to access warehouse {} without permission",
                    getCurrentUserIdentifier(), warehouseId);
            throw new ForbiddenException("You don't have access to this warehouse");
        }
    }

    public void validateWarehouseAccessOrThrow() {
        if (hasFullAccess()) {
            return;
        }

        if (getUserWarehouseIds().isEmpty()) {
            log.warn("User {} has no warehouse access", getCurrentUserIdentifier());
            throw new ForbiddenException("No warehouse access. Contact your administrator.");
        }
    }

    private UserPrincipal getCurrentPrincipalIfAvailable() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }

        return null;
    }

    private UUID getCurrentUserId() {
        UserPrincipal principal = getCurrentPrincipalIfAvailable();
        if (principal != null) {
            return principal.getId();
        }
        return null;
    }

    private String getCurrentUserIdentifier() {
        UUID userId = getCurrentUserId();
        if (userId != null) {
            return userId.toString();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }

        return "unknown";
    }
}
