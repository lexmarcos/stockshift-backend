package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt.cookie")
@Data
public class CookieProperties {
  private boolean secure = false;
  private String sameSite = "Lax";
  private String domain;
  private String path = "/";
  private boolean httpOnly = true;
}
