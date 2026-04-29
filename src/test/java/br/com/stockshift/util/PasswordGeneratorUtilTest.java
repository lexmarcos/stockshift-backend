package br.com.stockshift.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordGeneratorUtilTest {

    @Test
    void shouldGenerateDefaultAndCustomLengthPasswords() {
        String defaultPassword = PasswordGeneratorUtil.generateTemporaryPassword();
        String customPassword = PasswordGeneratorUtil.generateTemporaryPassword(12);

        assertThat(defaultPassword).hasSize(8);
        assertThat(customPassword).hasSize(12);
        assertThat(customPassword.chars().anyMatch(Character::isUpperCase)).isTrue();
        assertThat(customPassword.chars().anyMatch(Character::isLowerCase)).isTrue();
        assertThat(customPassword.chars().anyMatch(Character::isDigit)).isTrue();
    }

    @Test
    void shouldRejectPasswordsShorterThanMinimumCharacterTypes() {
        assertThatThrownBy(() -> PasswordGeneratorUtil.generateTemporaryPassword(3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 4");
    }
}
