package br.com.stockshift.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility class for extracting client IP addresses from HTTP requests.
 * Returns the direct peer address. Use ClientIpResolver when trusted proxy
 * headers should be considered.
 */
public final class IpUtil {

    private IpUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Extracts the direct client IP address from the HTTP request.
     *
     * @param request the HTTP request
     * @return the client IP address
     */
    public static String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
