package br.com.stockshift.service;

import br.com.stockshift.dto.permission.PermissionResponse;
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
                .map(code -> PermissionResponse.builder()
                        .id(permissionRepository.findByCodeIgnoreCase(code)
                                .map(permission -> permission.getId())
                                .orElse(stableIdFor(code)))
                        .code(code)
                        .description(code)
                        .build())
                .toList();
    }

    private UUID stableIdFor(String code) {
        // Deterministic UUID keeps UI/admin integration stable without requiring DB lookup.
        return UUID.nameUUIDFromBytes(("permission:" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
