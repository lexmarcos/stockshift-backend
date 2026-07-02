package br.com.stockshift.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CookieSecurityValidatorTest {

    @Test
    void shouldAllowInsecureCookiesInLocalProfiles() {
        assertThatCode(() -> validator(false, "dev").validateSecureCookiesOutsideLocalProfiles())
                .doesNotThrowAnyException();
        assertThatCode(() -> validator(false, "test").validateSecureCookiesOutsideLocalProfiles())
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInsecureCookiesOutsideLocalProfiles() {
        assertThatThrownBy(() -> validator(false, "prod").validateSecureCookiesOutsideLocalProfiles())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.cookie.secure");
    }

    @Test
    void shouldAllowSecureCookiesInProduction() {
        assertThatCode(() -> validator(true, "prod").validateSecureCookiesOutsideLocalProfiles())
                .doesNotThrowAnyException();
    }

    private CookieSecurityValidator validator(boolean secure, String profile) {
        CookieProperties cookieProperties = new CookieProperties();
        cookieProperties.setSecure(secure);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new CookieSecurityValidator(cookieProperties, environment);
    }
}
