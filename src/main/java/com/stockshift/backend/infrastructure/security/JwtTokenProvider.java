package com.stockshift.backend.infrastructure.security;

import com.stockshift.backend.infrastructure.config.TestUserProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;
    
    private final TestUserProperties testUserProperties;
    private final Environment environment;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails, accessTokenExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails, refreshTokenExpiration);
    }

    private String generateToken(Map<String, Object> extraClaims, UserDetails userDetails, Long expiration) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiration)))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        // Handle test tokens in development environment
        if (isTestToken(token)) {
            return testUserProperties.getUsername();
        }
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        // Test tokens never expire in development
        if (isTestToken(token)) {
            return Date.from(Instant.now().plusSeconds(31536000)); // 1 year from now
        }
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        // For test tokens, we need to handle claims differently
        if (isTestToken(token)) {
            // Create mock claims for test tokens
            Claims mockClaims = createMockClaimsForTestToken(token);
            return claimsResolver.apply(mockClaims);
        }
        
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * Create mock claims for test tokens
     */
    private Claims createMockClaimsForTestToken(String token) {
        // Create a simple JWT token to extract claims from
        String mockToken = Jwts.builder()
                .subject(testUserProperties.getUsername())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(31536000)))
                .signWith(getSigningKey())
                .compact();
        
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(mockToken)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        // Handle test tokens in development environment
        if (isTestToken(token)) {
            return isTestTokenValid(token, userDetails);
        }
        
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    
    /**
     * Check if the token is a test token (for development environment only)
     */
    private boolean isTestToken(String token) {
        if (!isDevelopmentEnvironment()) {
            return false;
        }
        
        return testUserProperties.getAccessToken().equals(token) || 
               testUserProperties.getRefreshToken().equals(token);
    }
    
    /**
     * Validate test token against user details
     */
    private boolean isTestTokenValid(String token, UserDetails userDetails) {
        if (!testUserProperties.isEnabled()) {
            return false;
        }
        
        // Check if the user is the test user
        boolean isTestUser = testUserProperties.getUsername().equals(userDetails.getUsername());
        
        // Check if token matches expected test tokens
        boolean isValidTestToken = testUserProperties.getAccessToken().equals(token) || 
                                 testUserProperties.getRefreshToken().equals(token);
        
        return isTestUser && isValidTestToken;
    }
    
    /**
     * Check if we're in development environment
     */
    private boolean isDevelopmentEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        
        // Check if 'dev' profile is active
        for (String profile : activeProfiles) {
            if ("dev".equals(profile)) {
                return true;
            }
        }
        
        // If no profiles are set, assume development (default behavior)
        return activeProfiles.length == 0;
    }
}
