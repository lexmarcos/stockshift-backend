package br.com.stockshift.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("warehouseGuard")
public class WarehouseGuard {

    public boolean isCurrent(UUID warehouseId) {
        if (warehouseId == null) {
            return false;
        }

        UUID currentWarehouseId = WarehouseContext.getWarehouseId();
        if (currentWarehouseId == null) {
            return hasFullAccess();
        }
        return warehouseId.equals(currentWarehouseId);
    }

    private boolean hasFullAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority) || "ROLE_SUPER_ADMIN".equals(authority));
    }
}
