package br.com.stockshift.service;

import br.com.stockshift.dto.role.RoleRequest;
import br.com.stockshift.dto.role.RoleResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.repository.PermissionRepository;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.SanitizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    public RoleResponse create(RoleRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Check if role with same name already exists
        if (roleRepository.findByTenantIdAndName(tenantId, request.getName()).isPresent()) {
            throw new BusinessException("Role with name '" + request.getName() + "' already exists");
        }

        // Sanitize input
        String sanitizedName = SanitizationUtil.sanitizeForHtml(request.getName());
        String sanitizedDescription = SanitizationUtil.sanitizeForHtml(request.getDescription());

        Role role = new Role();
        role.setTenantId(tenantId);
        role.setName(sanitizedName);
        role.setDescription(sanitizedDescription);
        role.setIsSystemRole(false);

        // Add permissions if provided
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            Set<Permission> permissions = new HashSet<>();
            for (UUID permissionId : request.getPermissionIds()) {
                Permission permission = permissionRepository.findById(permissionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", permissionId));
                permissions.add(permission);
            }
            role.setPermissions(permissions);
        }

        Role saved = roleRepository.save(role);
        log.info("Created role {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return roleRepository.findByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Role role = roleRepository.findByTenantId(tenantId).stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
        return mapToResponse(role);
    }

    @Transactional
    public RoleResponse update(UUID id, RoleRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        Role role = roleRepository.findByTenantId(tenantId).stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));

        if (role.getIsSystemRole()) {
            throw new BusinessException("System roles cannot be modified");
        }

        // Check if another role with same name exists
        roleRepository.findByTenantIdAndName(tenantId, request.getName())
                .filter(r -> !r.getId().equals(id))
                .ifPresent(r -> {
                    throw new BusinessException("Role with name '" + request.getName() + "' already exists");
                });

        // Sanitize input
        String sanitizedName = SanitizationUtil.sanitizeForHtml(request.getName());
        String sanitizedDescription = SanitizationUtil.sanitizeForHtml(request.getDescription());

        role.setName(sanitizedName);
        role.setDescription(sanitizedDescription);

        // Update permissions if provided
        if (request.getPermissionIds() != null) {
            Set<Permission> permissions = new HashSet<>();
            for (UUID permissionId : request.getPermissionIds()) {
                Permission permission = permissionRepository.findById(permissionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", permissionId));
                permissions.add(permission);
            }
            role.setPermissions(permissions);
        }

        Role updated = roleRepository.save(role);
        log.info("Updated role {} for tenant {}", id, tenantId);

        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Role role = roleRepository.findByTenantId(tenantId).stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));

        if (role.getIsSystemRole()) {
            throw new BusinessException("System roles cannot be deleted");
        }

        roleRepository.delete(role);

        log.info("Deleted role {} for tenant {}", id, tenantId);
    }

    private RoleResponse mapToResponse(Role role) {
        Set<RoleResponse.PermissionResponse> permissionResponses = role.getPermissions().stream()
                .map(p -> RoleResponse.PermissionResponse.builder()
                        .id(p.getId())
                        .code(p.getCode())
                        .description(p.getDescription())
                        .build())
                .collect(Collectors.toSet());

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isSystemRole(role.getIsSystemRole())
                .permissions(permissionResponses)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
