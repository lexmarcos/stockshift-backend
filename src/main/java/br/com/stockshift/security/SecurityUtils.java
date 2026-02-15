package br.com.stockshift.security;

import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }

        if (authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }

        String email = authentication.getName();
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            throw new UnauthorizedException("User not authenticated");
        }

        UUID tenantId = TenantContext.getTenantId();
        return (tenantId != null
                ? userRepository.findByTenantIdAndEmail(tenantId, email)
                : userRepository.findByEmail(email))
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
    }

    public UUID getCurrentWarehouseId() {
        UUID warehouseId = WarehouseContext.getWarehouseId();
        if (warehouseId == null) {
            throw new UnauthorizedException("No active warehouse context");
        }
        return warehouseId;
    }
}
