package br.com.stockshift.service;

import br.com.stockshift.dto.user.CreateUserRequest;
import br.com.stockshift.dto.user.CreateUserResponse;
import br.com.stockshift.dto.user.UpdateUserRequest;
import br.com.stockshift.dto.user.UserResponse;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.UserPrincipal;
import br.com.stockshift.util.PasswordGeneratorUtil;
import br.com.stockshift.util.SanitizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final WarehouseRepository warehouseRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {
        UUID tenantId = getCurrentTenantId();
        log.info("Creating user with email: {} for tenant: {}", request.getEmail(), tenantId);

        // Validate email uniqueness within tenant
        if (userRepository.findByTenantIdAndEmail(tenantId, request.getEmail()).isPresent()) {
            throw new BusinessException("Email already registered in this tenant");
        }

        // Validate and fetch roles
        Set<Role> roles = validateAndFetchRoles(request.getRoleIds(), tenantId);

        // Validate and fetch warehouses
        Set<Warehouse> warehouses = validateAndFetchWarehouses(request.getWarehouseIds(), tenantId);

        // Generate temporary password
        String temporaryPassword = PasswordGeneratorUtil.generateTemporaryPassword();

        // Create user
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setFullName(SanitizationUtil.sanitizeForHtml(request.getFullName()));
        user.setIsActive(true);
        user.setMustChangePassword(true);
        user.setRoles(roles);
        user.setWarehouses(warehouses);

        user = userRepository.save(user);
        log.info("Created user with ID: {} for tenant: {}", user.getId(), tenantId);

        List<String> roleNames = roles.stream()
                .map(Role::getName)
                .toList();

        List<String> warehouseNames = warehouses.stream()
                .map(Warehouse::getName)
                .toList();

        return CreateUserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .temporaryPassword(temporaryPassword)
                .mustChangePassword(true)
                .roles(roleNames)
                .warehouses(warehouseNames)
                .build();
    }

    private Set<Role> validateAndFetchRoles(Set<UUID> roleIds, UUID tenantId) {
        Set<Role> roles = new HashSet<>();

        for (UUID roleId : roleIds) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new BusinessException("Role not found: " + roleId));

            if (!role.getTenantId().equals(tenantId)) {
                throw new BusinessException("Role does not belong to this tenant: " + roleId);
            }

            roles.add(role);
        }

        return roles;
    }

    private Set<Warehouse> validateAndFetchWarehouses(Set<UUID> warehouseIds, UUID tenantId) {
        Set<Warehouse> warehouses = new HashSet<>();

        for (UUID warehouseId : warehouseIds) {
            Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)
                    .orElseThrow(() -> new BusinessException("Warehouse not found: " + warehouseId));

            if (!warehouse.getIsActive()) {
                throw new BusinessException("Warehouse is inactive: " + warehouseId);
            }

            warehouses.add(warehouse);
        }

        return warehouses;
    }

    private UUID getCurrentTenantId() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return principal.getTenantId();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        UUID tenantId = getCurrentTenantId();
        log.info("Listing users for tenant: {}", tenantId);

        return userRepository.findAllByTenantId(tenantId).stream()
                .map(this::toUserResponse)
                .toList();
    }

    private UserResponse toUserResponse(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        List<String> warehouseNames = user.getWarehouses().stream()
                .map(Warehouse::getName)
                .toList();

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .isActive(user.getIsActive())
                .mustChangePassword(user.getMustChangePassword())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .roles(roleNames)
                .warehouses(warehouseNames)
                .build();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        UUID tenantId = getCurrentTenantId();
        User user = userRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        UUID tenantId = getCurrentTenantId();
        log.info("Updating user: {} for tenant: {}", id, tenantId);

        User user = userRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Validate and fetch roles
        Set<Role> roles = validateAndFetchRoles(request.getRoleIds(), tenantId);

        // Validate and fetch warehouses
        Set<Warehouse> warehouses = validateAndFetchWarehouses(request.getWarehouseIds(), tenantId);

        // Update user fields
        user.setFullName(SanitizationUtil.sanitizeForHtml(request.getFullName()));
        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }
        user.setRoles(roles);
        user.setWarehouses(warehouses);

        user = userRepository.save(user);
        log.info("Updated user: {} for tenant: {}", id, tenantId);

        return toUserResponse(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        UUID tenantId = getCurrentTenantId();
        log.info("Deleting user: {} for tenant: {}", id, tenantId);

        User user = userRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Prevent self-deletion
        UserPrincipal currentUser = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        if (user.getId().equals(currentUser.getId())) {
            throw new BusinessException("Cannot delete your own account");
        }

        userRepository.delete(user);
        log.info("Deleted user: {} for tenant: {}", id, tenantId);
    }
}
