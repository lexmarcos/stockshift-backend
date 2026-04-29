package br.com.stockshift.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationTest {

    @Test
    void devProfileShouldUseEnvironmentVariablesForSecrets() throws IOException {
        Path devProfile = Path.of("src/main/resources/application-dev.yml");
        if (!Files.exists(devProfile)) {
            return;
        }
        String config = Files.readString(devProfile);

        assertThat(config).contains("secret: ${JWT_SECRET}");
        assertThat(config).contains("access-key: ${STORAGE_ACCESS_KEY:}");
        assertThat(config).contains("secret-key: ${STORAGE_SECRET_KEY:}");
    }
}
