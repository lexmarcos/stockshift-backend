package br.com.stockshift.util;

import br.com.stockshift.config.ClientIpProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final ClientIpProperties properties;

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedAddress = firstForwardedAddress(request);
        return StringUtils.hasText(forwardedAddress) ? forwardedAddress : remoteAddress;
    }

    private boolean isTrustedProxy(String remoteAddress) {
        if (!StringUtils.hasText(remoteAddress)) {
            return false;
        }
        return properties.getTrustedProxies().stream()
                .map(String::trim)
                .anyMatch(remoteAddress::equals);
    }

    private String firstForwardedAddress(HttpServletRequest request) {
        return firstPresentHeader(request, List.of(
                "CF-Connecting-IP",
                "X-Forwarded-For",
                "X-Real-IP"));
    }

    private String firstPresentHeader(HttpServletRequest request, List<String> names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (StringUtils.hasText(value)) {
                return value.split(",")[0].trim();
            }
        }
        return null;
    }
}
