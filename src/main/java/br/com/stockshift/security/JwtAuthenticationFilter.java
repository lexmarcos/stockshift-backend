package br.com.stockshift.security;

import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.service.PermissionResolverService;
import br.com.stockshift.service.TokenDenylistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenDenylistService tokenDenylistService;
    private final PermissionResolverService permissionResolverService;
    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            authenticateRequest(request);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            WarehouseContext.clear();
        }
    }

    private void authenticateRequest(HttpServletRequest request) {
        try {
            String jwt = getJwtFromRequest(request);
            if (!StringUtils.hasText(jwt) || !tokenProvider.validateToken(jwt)) {
                return;
            }
            authenticateToken(request, jwt);
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }
    }

    private void authenticateToken(HttpServletRequest request, String jwt) {
        String jti = tokenProvider.getJtiFromToken(jwt);
        if (tokenDenylistService.isDenylisted(jti)) {
            log.warn("Attempted use of revoked token: {}", jti);
            return;
        }
        authenticateAllowedPrincipal(request, jwt);
    }

    private void authenticateAllowedPrincipal(HttpServletRequest request, String jwt) {
        UUID userId = tokenProvider.getUserIdFromToken(jwt);
        UUID tenantId = tokenProvider.getTenantIdFromToken(jwt);
        UUID warehouseId = tokenProvider.getWarehouseIdFromToken(jwt);
        UserDetails userDetails = userDetailsService.loadUserById(userId.toString());
        if (!isCurrentPrincipalAllowed(userDetails, tenantId)) {
            return;
        }
        setAuthentication(request, userDetails, userId, tenantId, warehouseId);
    }

    private void setAuthentication(
            HttpServletRequest request,
            UserDetails userDetails,
            UUID userId,
            UUID tenantId,
            UUID warehouseId) {
        TenantContext.setTenantId(tenantId);
        setWarehouseContext(warehouseId);
        Collection<GrantedAuthority> authorities = resolveCurrentAuthorities(userDetails, userId, warehouseId);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean isCurrentPrincipalAllowed(UserDetails userDetails, UUID tenantId) {
        return userDetails.isEnabled()
                && isSameTenant(userDetails, tenantId)
                && isTenantActive(tenantId);
    }

    private boolean isSameTenant(UserDetails userDetails, UUID tenantId) {
        if (userDetails instanceof UserPrincipal principal) {
            return tenantId.equals(principal.getTenantId());
        }
        return true;
    }

    private boolean isTenantActive(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> Boolean.TRUE.equals(tenant.getIsActive()))
                .orElse(false);
    }

    private void setWarehouseContext(UUID warehouseId) {
        if (warehouseId != null) {
            WarehouseContext.setWarehouseId(warehouseId);
        }
    }

    private Collection<GrantedAuthority> resolveCurrentAuthorities(
            UserDetails userDetails,
            UUID userId,
            UUID warehouseId) {
        if (!(userDetails instanceof UserPrincipal) || warehouseId == null || hasGlobalAccess(userDetails)) {
            return new ArrayList<>(userDetails.getAuthorities());
        }

        Collection<GrantedAuthority> authorities = new ArrayList<>();
        addRoles(authorities, permissionResolverService.resolveUserRoleNames(userId, warehouseId));
        addPermissions(authorities, permissionResolverService.resolveUserPermissions(userId, warehouseId));
        return authorities;
    }

    private boolean hasGlobalAccess(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority) || "ROLE_SUPER_ADMIN".equals(authority));
    }

    private void addRoles(Collection<GrantedAuthority> authorities, Set<String> roles) {
        roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
    }

    private void addPermissions(Collection<GrantedAuthority> authorities, Set<String> permissions) {
        permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        // Priority 1: Read from accessToken cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // Priority 2: Fallback to Authorization header
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
