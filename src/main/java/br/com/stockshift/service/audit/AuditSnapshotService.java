package br.com.stockshift.service.audit;

import br.com.stockshift.model.entity.BaseEntity;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class AuditSnapshotService {

    private static final String MASK = "***MASKED***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "currentpassword",
            "newpassword",
            "accesstoken",
            "refreshtoken",
            "token",
            "captchatoken",
            "authorization",
            "cookie",
            "secret",
            "apikey");

    public Map<String, Object> snapshot(Object source) {
        if (source == null) {
            return Map.of();
        }
        if (source instanceof Map<?, ?> map) {
            return sanitizeMap(map);
        }
        return snapshotFields(source);
    }

    public List<String> diff(Map<String, Object> before, Map<String, Object> after) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(before != null ? before.keySet() : Set.of());
        keys.addAll(after != null ? after.keySet() : Set.of());
        return keys.stream()
                .filter(key -> !Objects.equals(value(before, key), value(after, key)))
                .toList();
    }

    private Object value(Map<String, Object> values, String key) {
        return values != null ? values.get(key) : null;
    }

    private Map<String, Object> snapshotFields(Object source) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Field field : fieldsFor(source.getClass())) {
            putFieldValue(snapshot, source, field);
        }
        return snapshot;
    }

    private void putFieldValue(Map<String, Object> snapshot, Object source, Field field) {
        if (shouldSkip(field)) {
            return;
        }
        try {
            field.setAccessible(true);
            snapshot.put(field.getName(), sanitizeValue(field.getName(), field.get(source)));
        } catch (IllegalAccessException ignored) {
            snapshot.put(field.getName(), null);
        }
    }

    private List<Field> fieldsFor(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(List.of(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean shouldSkip(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers) || field.isSynthetic();
    }

    private Object sanitizeValue(String key, Object value) {
        if (isSensitive(key)) {
            return MASK;
        }
        return normalizeValue(value);
    }

    private Object normalizeValue(Object value) {
        if (value == null || isSimpleValue(value)) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::normalizeValue).toList();
        }
        return normalizeAssociation(value);
    }

    private Object normalizeAssociation(Object value) {
        if (value instanceof BaseEntity entity) {
            return entity.getId();
        }
        UUID reflectedId = readId(value);
        return reflectedId != null ? reflectedId : value.toString();
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> map) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        map.forEach((key, value) -> sanitized.put(String.valueOf(key), sanitizeValue(String.valueOf(key), value)));
        return sanitized;
    }

    private UUID readId(Object value) {
        try {
            Method method = value.getClass().getMethod("getId");
            Object id = method.invoke(value);
            return id instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof UUID
                || value instanceof BigDecimal
                || value instanceof TemporalAccessor;
    }

    private boolean isSensitive(String key) {
        return SENSITIVE_KEYS.contains(key.replace("_", "").replace("-", "").toLowerCase());
    }
}
