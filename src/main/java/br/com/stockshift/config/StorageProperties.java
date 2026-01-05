package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "storage")
@Data
public class StorageProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String region = "us-east-1";
    private String publicUrl;
}
