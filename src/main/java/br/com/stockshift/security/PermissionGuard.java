package br.com.stockshift.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("permissionGuard")
public class PermissionGuard {

    public boolean isAdmin() {
        Set<String> authorities = currentAuthorities();
        return authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_SUPER_ADMIN");
    }

    public boolean has(String requiredAuthority) {
        return hasAny(requiredAuthority);
    }

    public boolean hasAny(String... requiredAuthorities) {
        Set<String> rawAuthorities = currentAuthorities();

        if (rawAuthorities.contains("ROLE_ADMIN") || rawAuthorities.contains("ROLE_SUPER_ADMIN")) {
            return true;
        }

        Set<String> normalizedAuthorities = rawAuthorities.stream()
                .map(authority -> authority.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return Arrays.stream(requiredAuthorities)
                .filter(requiredAuthority -> requiredAuthority != null && !requiredAuthority.isBlank())
                .map(requiredAuthority -> requiredAuthority.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedAuthorities::contains);
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
