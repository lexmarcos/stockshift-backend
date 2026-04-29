package br.com.stockshift.service;

import br.com.stockshift.config.HCaptchaProperties;
import br.com.stockshift.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HCaptchaServiceTest {

    @Test
    void validateCaptchaShouldSkipWhenDisabledAndRequireTokenWhenEnabled() {
        HCaptchaProperties properties = new HCaptchaProperties();
        properties.setEnabled(false);
        HCaptchaService service = new HCaptchaService(properties);

        assertThat(service.validateCaptcha(null)).isTrue();

        properties.setEnabled(true);
        assertThatThrownBy(() -> service.validateCaptcha(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Captcha token is required");
    }

    @Test
    void validateCaptchaShouldReturnTrueForSuccessfulProviderResponse() {
        HCaptchaProperties properties = properties();
        HCaptchaService service = serviceWithRestTemplate(properties,
                (ResponseEntity<Map>) (ResponseEntity<?>) ResponseEntity.ok(Map.of("success", true)));

        assertThat(service.validateCaptcha("token")).isTrue();
    }

    @Test
    void validateCaptchaShouldRejectFailedEmptyAndErroredProviderResponses() {
        HCaptchaProperties properties = properties();
        HCaptchaService failedService = serviceWithRestTemplate(properties,
                (ResponseEntity<Map>) (ResponseEntity<?>) ResponseEntity.ok(Map.of("success", false, "error-codes", "bad")));
        assertThatThrownBy(() -> failedService.validateCaptcha("token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid captcha");

        HCaptchaService emptyService = serviceWithRestTemplate(properties,
                (ResponseEntity<Map>) (ResponseEntity<?>) ResponseEntity.ok(null));
        assertThatThrownBy(() -> emptyService.validateCaptcha("token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to validate captcha");

        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("network"));
        HCaptchaService erroredService = new HCaptchaService(properties);
        ReflectionTestUtils.setField(erroredService, "restTemplate", restTemplate);
        assertThatThrownBy(() -> erroredService.validateCaptcha("token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Please try again");
    }

    private HCaptchaProperties properties() {
        HCaptchaProperties properties = new HCaptchaProperties();
        properties.setEnabled(true);
        properties.setSecretKey("secret");
        return properties;
    }

    private HCaptchaService serviceWithRestTemplate(
            HCaptchaProperties properties,
            ResponseEntity<Map> response) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class))).thenReturn(response);
        HCaptchaService service = new HCaptchaService(properties);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        return service;
    }
}
