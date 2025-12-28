package br.com.stockshift.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // Parse allowed origins from comma-separated string
    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));

    // Allow all HTTP methods
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

    // Allow all headers
    configuration.setAllowedHeaders(Arrays.asList("*"));

    // Allow credentials (cookies, authorization headers)
    configuration.setAllowCredentials(true);

    // Cache preflight response for 1 hour
    configuration.setMaxAge(3600L);

    // Expose authorization header
    configuration.setExposedHeaders(Arrays.asList("Authorization"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    return source;
  }
}
