package br.com.stockshift.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoragePropertiesTest {

    @Test
    void shouldBindBucketNameFromStorageBucketEnvironmentVariable() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", Map.of(
                "STORAGE_ENDPOINT", "https://r2.example.com",
                "STORAGE_ACCESS_KEY", "test-access-key",
                "STORAGE_SECRET_KEY", "test-secret-key",
                "STORAGE_BUCKET", "stockshift",
                "STORAGE_PUBLIC_URL", "https://cdn.example.com"
        )));

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));

        StorageProperties properties = Binder.get(environment)
                .bind("storage", StorageProperties.class)
                .orElseThrow(() -> new AssertionError("storage properties were not bound"));

        assertThat(properties.getBucketName()).isEqualTo("stockshift");
    }
}
