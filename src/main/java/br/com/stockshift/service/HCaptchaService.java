package br.com.stockshift.service;

import br.com.stockshift.config.HCaptchaProperties;
import br.com.stockshift.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class HCaptchaService {

    private static final String HCAPTCHA_VERIFY_URL = "https://api.hcaptcha.com/siteverify";

    private final HCaptchaProperties hCaptchaProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Validates the hCaptcha token with hCaptcha's API.
     *
     * @param captchaToken the token received from the frontend
     * @return true if the captcha is valid
     * @throws BusinessException if captcha validation fails
     */
    public boolean validateCaptcha(String captchaToken) {
        // Skip validation if captcha is disabled (e.g., in tests)
        if (!hCaptchaProperties.isEnabled()) {
            log.debug("Captcha validation skipped - captcha is disabled");
            return true;
        }

        if (captchaToken == null || captchaToken.isBlank()) {
            throw new BusinessException("Captcha token is required");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", hCaptchaProperties.getSecretKey());
            body.add("response", captchaToken);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    HCAPTCHA_VERIFY_URL,
                    request,
                    Map.class
            );

            if (response.getBody() != null) {
                Boolean success = (Boolean) response.getBody().get("success");
                if (Boolean.TRUE.equals(success)) {
                    log.debug("Captcha validation successful");
                    return true;
                } else {
                    log.warn("Captcha validation failed: {}", response.getBody().get("error-codes"));
                    throw new BusinessException("Invalid captcha. Please try again.");
                }
            }

            throw new BusinessException("Failed to validate captcha");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating captcha: {}", e.getMessage());
            throw new BusinessException("Failed to validate captcha. Please try again.");
        }
    }
}
