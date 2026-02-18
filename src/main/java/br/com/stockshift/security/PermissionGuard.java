package br.com.stockshift.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("permissionGuard")
public class PermissionGuard {

    public boolean isAdmin() {
        return currentAuthorities().contains("ROLE_ADMIN");
    }

    public boolean hasAny(String... requiredAuthorities) {
        Set<String> authorities = currentAuthorities();

        if (authorities.contains("ROLE_ADMIN")) {
            return true;
        }

        return Arrays.stream(requiredAuthorities)
                .anyMatch(authorities::contains);
    }

    private Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }

        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .collect(Collectors.toSet());
    }
}
