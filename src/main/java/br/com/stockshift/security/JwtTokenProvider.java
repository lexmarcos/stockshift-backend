package br.com.stockshift.security;

import br.com.stockshift.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId, UUID tenantId, String email, List<String> roles, List<String> authorities) {
        return generateAccessToken(userId, tenantId, null, email, roles, authorities);
    }

    public String generateAccessToken(UUID userId, UUID tenantId, UUID warehouseId, String email, List<String> roles, List<String> authorities) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessExpiration());
        String jti = UUID.randomUUID().toString();

        var builder = Jwts.builder()
                .id(jti)
                .subject(userId.toString())
                .claim("tenantId", tenantId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .claim("authorities", authorities)
                .claim("permissions", authorities) // Backward compatibility for legacy consumers.
                .issuedAt(now)
                .expiration(expiryDate);

        if (warehouseId != null) {
            builder.claim("warehouseId", warehouseId.toString());
        }

        return builder.signWith(getSigningKey(), Jwts.SIG.HS256).compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getSubject());
    }

    public UUID getTenantIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.get("tenantId", String.class));
    }

    public UUID getWarehouseIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String warehouseId = claims.get("warehouseId", String.class);
        return warehouseId != null ? UUID.fromString(warehouseId) : null;
    }

    public List<String> getAuthoritiesFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Object rawAuthorities = claims.get("authorities");
        if (rawAuthorities == null) {
            rawAuthorities = claims.get("permissions");
        }

        if (!(rawAuthorities instanceof List<?> list)) {
            return Collections.emptyList();
        }

        List<String> authorities = new ArrayList<>();
        for (Object value : list) {
            if (value != null) {
                authorities.add(value.toString());
            }
        }
        return authorities;
    }

    public List<String> getRolesFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Object rawRoles = claims.get("roles");
        if (!(rawRoles instanceof List<?> list)) {
            return Collections.emptyList();
        }

        List<String> roles = new ArrayList<>();
        for (Object value : list) {
            if (value != null) {
                roles.add(value.toString());
            }
        }
        return roles;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature");
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty");
        }
        return false;
    }

    public String getJtiFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getId();
    }

    public long getRemainingTtl(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Date expiration = claims.getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }
}
