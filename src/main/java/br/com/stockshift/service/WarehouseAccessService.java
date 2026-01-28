package br.com.stockshift.service;

import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseAccessService {

    public Set<UUID> getUserWarehouseIds() {
        UserPrincipal principal = getCurrentPrincipal();
        return principal.getWarehouseIds();
    }

    public boolean hasFullAccess() {
        UserPrincipal principal = getCurrentPrincipal();
        return principal.isHasFullAccess();
    }

    public UUID getTenantId() {
        UserPrincipal principal = getCurrentPrincipal();
        return principal.getTenantId();
    }

    public void validateWarehouseAccess(UUID warehouseId) {
        if (hasFullAccess()) {
            return;
        }

        Set<UUID> userWarehouseIds = getUserWarehouseIds();
        if (!userWarehouseIds.contains(warehouseId)) {
            log.warn("User {} attempted to access warehouse {} without permission",
                    getCurrentPrincipal().getId(), warehouseId);
            throw new ForbiddenException("You don't have access to this warehouse");
        }
    }

    public void validateWarehouseAccessOrThrow() {
        if (hasFullAccess()) {
            return;
        }

        Set<UUID> userWarehouseIds = getUserWarehouseIds();
        if (userWarehouseIds == null || userWarehouseIds.isEmpty()) {
            log.warn("User {} has no warehouse access", getCurrentPrincipal().getId());
            throw new ForbiddenException("No warehouse access. Contact your administrator.");
        }
    }

    private UserPrincipal getCurrentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
