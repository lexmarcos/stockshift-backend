package br.com.stockshift.security;

import br.com.stockshift.config.BotAuthenticationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BotAuthenticationFilterTest {

    private static final String API_KEY = "test-bot-key";
    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private BotAuthenticationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        BotAuthenticationProperties properties = new BotAuthenticationProperties();
        properties.setApiKey(API_KEY);
        properties.setTenantId(tenantId);
        filter = new BotAuthenticationFilter(properties);
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        WarehouseContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateInternalBotRouteWithValidKey() throws ServletException, IOException {
        MockHttpServletRequest request = internalRequest("/api/internal/bot/warehouses");
        request.addHeader(BotAuthenticationFilter.HEADER_NAME, API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting(authority -> authority.getAuthority())
                    .contains(BotAuthenticationFilter.BOT_AUTHORITY);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectInternalBotRouteWithoutKey() throws ServletException, IOException {
        MockHttpServletRequest request = internalRequest("/api/internal/bot/warehouses");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectInternalBotRouteWithInvalidKey() throws ServletException, IOException {
        MockHttpServletRequest request = internalRequest("/api/internal/bot/warehouses");
        request.addHeader(BotAuthenticationFilter.HEADER_NAME, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateNormalApiRouteWithBotKey() throws ServletException, IOException {
        MockHttpServletRequest request = internalRequest("/api/products");
        request.addHeader(BotAuthenticationFilter.HEADER_NAME, API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest internalRequest(String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", servletPath);
        request.setServletPath(servletPath);
        return request;
    }
}
