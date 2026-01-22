package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rate-limit.login")
@Data
public class RateLimitProperties {

    /**
     * Maximum number of login attempts allowed in the time window.
     */
    private int capacity = 5;

    /**
     * Number of tokens to refill after the duration expires.
     */
    private int refillTokens = 5;

    /**
     * Duration in minutes for the refill period.
     */
    private int refillDurationMinutes = 15;
}
