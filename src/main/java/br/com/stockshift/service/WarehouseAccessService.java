package br.com.stockshift.service;

import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.security.UserPrincipal;
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

    public Set<UUID> getUserWarehouseIds() {
        UserPrincipal principal = getCurrentPrincipalIfAvailable();
        if (principal == null || principal.getWarehouseIds() == null) {
            return Set.of();
        }
        return principal.getWarehouseIds();
    }

    public boolean hasFullAccess() {
        UserPrincipal principal = getCurrentPrincipalIfAvailable();
        if (principal != null) {
            return principal.isHasFullAccess();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    public UUID getTenantId() {
        UserPrincipal principal = getCurrentPrincipalIfAvailable();
        if (principal != null) {
            return principal.getTenantId();
        }
        return TenantContext.getTenantId();
    }

    public void validateWarehouseAccess(UUID warehouseId) {
        if (hasFullAccess()) {
            return;
        }

        Set<UUID> userWarehouseIds = getUserWarehouseIds();
        if (!userWarehouseIds.contains(warehouseId)) {
            log.warn("User {} attempted to access warehouse {} without permission",
                    getCurrentUserIdentifier(), warehouseId);
            throw new ForbiddenException("You don't have access to this warehouse");
        }
    }

    public void validateWarehouseAccessOrThrow() {
        if (hasFullAccess()) {
            return;
        }

        Set<UUID> userWarehouseIds = getUserWarehouseIds();
        if (userWarehouseIds == null || userWarehouseIds.isEmpty()) {
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

    private String getCurrentUserIdentifier() {
        UserPrincipal principal = getCurrentPrincipalIfAvailable();
        if (principal != null && principal.getId() != null) {
            return principal.getId().toString();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }

        return "unknown";
    }
}
