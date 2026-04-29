package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "security.ip")
@Data
public class ClientIpProperties {
    private List<String> trustedProxies = new ArrayList<>(List.of(
            "127.0.0.1",
            "0:0:0:0:0:0:0:1",
            "::1"));
}
