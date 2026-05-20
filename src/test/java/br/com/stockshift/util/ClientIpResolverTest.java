package br.com.stockshift.util;

import br.com.stockshift.config.ClientIpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void shouldUseForwardedAddressWhenRemoteAddressMatchesTrustedIpv4Cidr() {
        ClientIpResolver resolver = resolverWithTrustedProxies("172.16.0.0/12");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.4");
        request.addHeader("X-Forwarded-For", "198.51.100.77, 172.18.0.4");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.77");
    }

    @Test
    void shouldUseConnectionAddressWhenRemoteAddressIsOutsideTrustedCidr() {
        ClientIpResolver resolver = resolverWithTrustedProxies("172.16.0.0/12");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.77");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void shouldSupportTrustedIpv6Cidr() {
        ClientIpResolver resolver = resolverWithTrustedProxies("fd00::/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("fd00::12");
        request.addHeader("X-Forwarded-For", "2001:db8::77");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::77");
    }

    @Test
    void shouldIgnoreMalformedTrustedCidrEntries() {
        ClientIpResolver resolver = resolverWithTrustedProxies("172.16.0.0/bad", "127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.4");
        request.addHeader("X-Forwarded-For", "198.51.100.77");

        assertThat(resolver.resolve(request)).isEqualTo("172.18.0.4");
    }

    private ClientIpResolver resolverWithTrustedProxies(String... trustedProxies) {
        ClientIpProperties properties = new ClientIpProperties();
        properties.setTrustedProxies(List.of(trustedProxies));
        return new ClientIpResolver(properties);
    }
}
