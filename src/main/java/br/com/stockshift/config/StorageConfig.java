package br.com.stockshift.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("!'${storage.endpoint:}'.isEmpty()")
public class StorageConfig {
    private final StorageProperties properties;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKey(),
                properties.getSecretKey());

        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true)
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(30))
                        .socketTimeout(Duration.ofSeconds(30)))
                .build();
    }

    /**
     * Template for the thumbnail-row swap in {@code ProductImageProcessingService}.
     *
     * <p>Uses REQUIRES_NEW because the swap can be invoked from a {@code afterCommit()}
     * synchronization callback (inline products created via stock movements, PR #5 review).
     * At that point the outer transaction is already committed but its resources are still
     * bound to the thread, so a REQUIRED template would silently join the dead transaction
     * and the {@code deleteAll}/{@code saveAll} would never commit — leaving orphaned R2
     * objects and empty {@code ProductResponse.thumbnails}. A new transaction guarantees the
     * swap commits. On the admin-job path (no outer transaction) this behaves like REQUIRED.
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
