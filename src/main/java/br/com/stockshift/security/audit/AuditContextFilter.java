package br.com.stockshift.security.audit;

import br.com.stockshift.security.TenantContext;
import br.com.stockshift.security.UserPrincipal;
import br.com.stockshift.security.WarehouseContext;
import br.com.stockshift.util.IpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class AuditContextFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            AuditContextHolder.set(buildContext(request, response, requestId));
            filterChain.doFilter(request, response);
        } finally {
            AuditContextHolder.setHttpStatus(response.getStatus());
            AuditContextHolder.clear();
        }
    }

    private AuditContext buildContext(
            HttpServletRequest request,
            HttpServletResponse response,
            String requestId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = authentication != null && authentication.getPrincipal() instanceof UserPrincipal value
                ? value
                : null;

        return AuditContext.builder()
                .tenantId(principal != null ? principal.getTenantId() : TenantContext.getTenantId())
                .actorUserId(principal != null ? principal.getId() : null)
                .actorEmail(principal != null ? principal.getEmail() : resolveAuthenticationName(authentication))
                .warehouseId(WarehouseContext.getWarehouseId())
                .requestId(requestId)
                .httpMethod(request.getMethod())
                .httpPath(request.getRequestURI())
                .httpStatus(response.getStatus())
                .ipAddress(IpUtil.getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .build();
    }

    private String resolveAuthenticationName(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String name = authentication.getName();
        return "anonymousUser".equals(name) ? null : name;
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
