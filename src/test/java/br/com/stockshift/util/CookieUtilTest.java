package br.com.stockshift.util;

import br.com.stockshift.config.CookieProperties;
import br.com.stockshift.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilTest {

    @Test
    void shouldAddAndRemoveTokenCookiesWithDomain() {
        CookieProperties cookieProperties = cookieProperties("example.com");
        JwtProperties jwtProperties = jwtProperties();
        CookieUtil util = new CookieUtil(cookieProperties, jwtProperties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        util.addAccessTokenCookie(response, "access");
        util.addRefreshTokenCookie(response, "refresh");
        util.removeAccessTokenCookie(response);
        util.removeRefreshTokenCookie(response);

        assertThat(response.getHeaders("Set-Cookie")).hasSize(4);
        assertThat(response.getHeaders("Set-Cookie").get(0)).contains("accessToken=access");
        assertThat(response.getHeaders("Set-Cookie").get(0)).contains("Domain=example.com");
        assertThat(response.getHeaders("Set-Cookie").get(1)).contains("refreshToken=refresh");
        assertThat(response.getHeaders("Set-Cookie").get(2)).contains("Max-Age=0");
    }

    @Test
    void shouldHandleCookiesWithoutDomainAndReadTokens() {
        CookieUtil util = new CookieUtil(cookieProperties(null), jwtProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        util.addAccessTokenCookie(response, "access");
        util.addRefreshTokenCookie(response, "refresh");

        Cookie[] cookies = {
                new Cookie("accessToken", "access"),
                new Cookie("refreshToken", "refresh"),
                new Cookie("other", "ignored")
        };
        assertThat(response.getHeaders("Set-Cookie").get(0)).doesNotContain("Domain=");
        assertThat(util.getAccessTokenFromCookie(cookies)).isEqualTo("access");
        assertThat(util.getRefreshTokenFromCookie(cookies)).isEqualTo("refresh");
        assertThat(util.getAccessTokenFromCookie(null)).isNull();
        assertThat(util.getRefreshTokenFromCookie(null)).isNull();
        assertThat(util.getRefreshTokenFromCookie(new Cookie[] { new Cookie("other", "x") })).isNull();
    }

    private CookieProperties cookieProperties(String domain) {
        CookieProperties properties = new CookieProperties();
        properties.setSecure(true);
        properties.setSameSite("Strict");
        properties.setPath("/");
        properties.setHttpOnly(true);
        properties.setDomain(domain);
        return properties;
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessExpiration(60_000L);
        properties.setRefreshExpiration(120_000L);
        return properties;
    }
}
