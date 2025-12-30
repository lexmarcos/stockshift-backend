package br.com.stockshift.util;

import br.com.stockshift.config.CookieProperties;
import br.com.stockshift.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CookieUtil {
    
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    
    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;
    
    public void addAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(cookieProperties.isHttpOnly())
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .maxAge(Duration.ofMillis(jwtProperties.getAccessExpiration()))
                .sameSite(cookieProperties.getSameSite())
                .build();
        
        if (cookieProperties.getDomain() != null && !cookieProperties.getDomain().isEmpty()) {
            cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                    .httpOnly(cookieProperties.isHttpOnly())
                    .secure(cookieProperties.isSecure())
                    .path(cookieProperties.getPath())
                    .maxAge(Duration.ofMillis(jwtProperties.getAccessExpiration()))
                    .sameSite(cookieProperties.getSameSite())
                    .domain(cookieProperties.getDomain())
                    .build();
        }
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    public void addRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(cookieProperties.isHttpOnly())
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .maxAge(Duration.ofMillis(jwtProperties.getRefreshExpiration()))
                .sameSite(cookieProperties.getSameSite())
                .build();
        
        if (cookieProperties.getDomain() != null && !cookieProperties.getDomain().isEmpty()) {
            cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                    .httpOnly(cookieProperties.isHttpOnly())
                    .secure(cookieProperties.isSecure())
                    .path(cookieProperties.getPath())
                    .maxAge(Duration.ofMillis(jwtProperties.getRefreshExpiration()))
                    .sameSite(cookieProperties.getSameSite())
                    .domain(cookieProperties.getDomain())
                    .build();
        }
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    public void removeAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .maxAge(0)
                .sameSite(cookieProperties.getSameSite())
                .build();
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    public void removeRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .maxAge(0)
                .sameSite(cookieProperties.getSameSite())
                .build();
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    public String getRefreshTokenFromCookie(Cookie[] cookies) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
