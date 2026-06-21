package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Configuration
@ConfigurationProperties(prefix = "stockshift.bot")
@Data
public class BotAuthenticationProperties {
    private String apiKey;
    private UUID tenantId;

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && tenantId != null;
    }
}
