package br.com.stockshift.service.audit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSnapshotServiceTest {

    private final AuditSnapshotService service = new AuditSnapshotService();

    @Test
    void snapshotShouldMaskSensitiveFieldsRecursively() {
        Map<String, Object> snapshot = service.snapshot(Map.of(
                "email", "user@test.com",
                "password", "plain",
                "nested", Map.of("apiKey", "key", "safe", "value"),
                "items", List.of(Map.of("token", "abc", "name", "item"))));

        assertThat(snapshot.get("password")).isEqualTo("***MASKED***");
        assertThat(snapshot.get("email")).isEqualTo("user@test.com");
        Map<?, ?> nested = (Map<?, ?>) snapshot.get("nested");
        assertThat(nested.get("apiKey")).isEqualTo("***MASKED***");
        assertThat(nested.get("safe")).isEqualTo("value");
        assertThat((List<?>) snapshot.get("items"))
                .first()
                .satisfies(item -> assertThat(((Map<?, ?>) item).get("token")).isEqualTo("***MASKED***"));
    }

    @Test
    void diffShouldReturnChangedFieldNames() {
        Map<String, Object> before = Map.of("name", "Old", "active", true, "sku", "A");
        Map<String, Object> after = Map.of("name", "New", "active", true, "barcode", "123");

        assertThat(service.diff(before, after))
                .containsExactlyInAnyOrder("name", "sku", "barcode");
    }
}
