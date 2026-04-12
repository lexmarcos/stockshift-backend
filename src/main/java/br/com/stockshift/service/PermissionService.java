package br.com.stockshift.service;

import br.com.stockshift.dto.permission.PermissionResponse;
import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.repository.PermissionRepository;
import br.com.stockshift.security.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll() {
        return PermissionCodes.all().stream()
                .sorted()
                .map(code -> {
                    Permission dbPermission = permissionRepository.findByCodeIgnoreCase(code).orElse(null);

                    String resource = null;
                    String action = null;
                    String scope = null;
                    String[] parts = code.split(":");
                    if (parts.length >= 1) resource = parts[0];
                    if (parts.length >= 2) action = parts[1];
                    if (parts.length >= 3) scope = parts[2];

                    // Use DB values if available, otherwise fall back to parsed values
                    if (dbPermission != null) {
                        if (dbPermission.getResource() != null) resource = dbPermission.getResource();
                        if (dbPermission.getAction() != null) action = dbPermission.getAction();
                        if (dbPermission.getScope() != null) scope = dbPermission.getScope();
                    }

                    return PermissionResponse.builder()
                            .id(dbPermission != null ? dbPermission.getId() : stableIdFor(code))
                            .code(code)
                            .description(dbPermission != null && dbPermission.getDescription() != null
                                    ? dbPermission.getDescription() : code)
                            .resource(resource)
                            .action(action)
                            .scope(scope)
                            .build();
                })
                .toList();
    }

    private UUID stableIdFor(String code) {
        // Deterministic UUID keeps UI/admin integration stable without requiring DB lookup.
        return UUID.nameUUIDFromBytes(("permission:" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
