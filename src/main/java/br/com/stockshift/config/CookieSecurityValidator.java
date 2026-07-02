package br.com.stockshift.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CookieSecurityValidator {

    private static final Set<String> INSECURE_COOKIE_PROFILES = Set.of("dev", "test");

    private final CookieProperties cookieProperties;
    private final Environment environment;

    @PostConstruct
    void validateSecureCookiesOutsideLocalProfiles() {
        if (cookieProperties.isSecure()) {
            return;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        boolean localOnly = activeProfiles.length > 0
                && Arrays.stream(activeProfiles).allMatch(INSECURE_COOKIE_PROFILES::contains);
        if (localOnly) {
            return;
        }

        throw new IllegalStateException(
                "jwt.cookie.secure must be true outside dev/test profiles");
    }
}
