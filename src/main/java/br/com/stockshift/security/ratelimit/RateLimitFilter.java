package br.com.stockshift.security.ratelimit;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.config.ClientIpProperties;
import br.com.stockshift.util.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String LOGIN_PATH = "/stockshift/api/auth/login";

    public RateLimitFilter(RateLimitService rateLimitService) {
        this(rateLimitService, new ClientIpResolver(new ClientIpProperties()));
    }

    @Autowired
    public RateLimitFilter(RateLimitService rateLimitService, ClientIpResolver clientIpResolver) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!isLoginRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = clientIpResolver.resolve(request);
        log.debug("Rate limit check for IP: {} on path: {}", clientIp, request.getRequestURI());

        if (!rateLimitService.tryConsume(clientIp)) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);

            long retryAfter = rateLimitService.getRetryAfterSeconds();

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfter));

            ApiResponse<?> error = ApiResponse.error(
                    "Muitas tentativas de login. Tente novamente em " +
                    (retryAfter / 60) + " minutos."
            );

            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && LOGIN_PATH.equals(request.getRequestURI());
    }

}
