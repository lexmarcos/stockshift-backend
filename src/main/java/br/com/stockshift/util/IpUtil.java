package br.com.stockshift.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility class for extracting client IP addresses from HTTP requests.
 * Handles various proxy headers to get the real client IP.
 */
public final class IpUtil {

    private IpUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Extracts the client IP address, considering proxy headers.
     * Priority: CF-Connecting-IP > X-Forwarded-For > X-Real-IP > RemoteAddr
     *
     * @param request the HTTP request
     * @return the client IP address
     */
    public static String getClientIp(HttpServletRequest request) {
        // Cloudflare
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp.trim();
        }

        // Standard proxy header
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, first one is the original client
            return xForwardedFor.split(",")[0].trim();
        }

        // Nginx proxy
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }
}
