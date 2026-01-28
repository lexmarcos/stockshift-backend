package br.com.stockshift.service;

import br.com.stockshift.dto.permission.PermissionResponse;
import br.com.stockshift.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll() {
        return permissionRepository.findAll().stream()
                .map(permission -> PermissionResponse.builder()
                        .id(permission.getId())
                        .resource(permission.getResource().name())
                        .resourceDisplayName(permission.getResource().getDisplayName())
                        .action(permission.getAction().name())
                        .actionDisplayName(permission.getAction().getDisplayName())
                        .scope(permission.getScope().name())
                        .scopeDisplayName(permission.getScope().getDisplayName())
                        .description(permission.getDescription())
                        .build())
                .toList();
    }
}
