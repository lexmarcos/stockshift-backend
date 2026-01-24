package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hcaptcha")
@Data
public class HCaptchaProperties {
    private String secretKey;
    private boolean enabled = true;
}
