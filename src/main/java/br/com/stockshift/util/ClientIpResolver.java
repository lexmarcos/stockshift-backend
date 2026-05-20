package br.com.stockshift.util;

import br.com.stockshift.config.ClientIpProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
                .filter(StringUtils::hasText)
                .anyMatch(trustedProxy -> matchesTrustedProxy(remoteAddress, trustedProxy));
    }

    private boolean matchesTrustedProxy(String remoteAddress, String trustedProxy) {
        if (trustedProxy.contains("/")) {
            return isInCidr(remoteAddress, trustedProxy);
        }
        return remoteAddress.equals(trustedProxy);
    }

    private boolean isInCidr(String remoteAddress, String cidr) {
        String[] parts = cidr.split("/", -1);
        if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            return false;
        }

        try {
            InetAddress remote = InetAddress.getByName(remoteAddress);
            InetAddress network = InetAddress.getByName(parts[0]);
            byte[] remoteBytes = remote.getAddress();
            byte[] networkBytes = network.getAddress();
            if (remoteBytes.length != networkBytes.length) {
                return false;
            }

            int prefixLength = Integer.parseInt(parts[1]);
            int bitLength = remoteBytes.length * 8;
            if (prefixLength < 0 || prefixLength > bitLength) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (remoteBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            if (remainingBits == 0) {
                return true;
            }

            int mask = (0xff << (8 - remainingBits)) & 0xff;
            return (remoteBytes[fullBytes] & 0xff & mask) == (networkBytes[fullBytes] & 0xff & mask);
        } catch (UnknownHostException | NumberFormatException ignored) {
            return false;
        }
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
