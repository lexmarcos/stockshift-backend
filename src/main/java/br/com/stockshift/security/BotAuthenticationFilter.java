package br.com.stockshift.security;

import br.com.stockshift.config.BotAuthenticationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BotAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-StockShift-Bot-Key";
    public static final String BOT_AUTHORITY = "bot:internal";
    private static final PathPatternRequestMatcher BOT_ROUTE_MATCHER =
            PathPatternRequestMatcher.pathPattern("/api/internal/bot/**");

    private final BotAuthenticationProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !BOT_ROUTE_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isValidRequest(request)) {
            reject(response);
            return;
        }

        try {
            authenticateBotRequest(request);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            WarehouseContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isValidRequest(HttpServletRequest request) {
        String submittedKey = request.getHeader(HEADER_NAME);
        if (!properties.isConfigured() || !StringUtils.hasText(submittedKey)) {
            return false;
        }
        byte[] submitted = submittedKey.getBytes(StandardCharsets.UTF_8);
        byte[] expected = properties.getApiKey().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(submitted, expected);
    }

    private void authenticateBotRequest(HttpServletRequest request) {
        TenantContext.setTenantId(properties.getTenantId());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                new BotPrincipal(properties.getTenantId()),
                null,
                List.of(new SimpleGrantedAuthority(BOT_AUTHORITY)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"Invalid bot API key\"}");
    }
}
