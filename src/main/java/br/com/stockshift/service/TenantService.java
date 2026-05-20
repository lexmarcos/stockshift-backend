package br.com.stockshift.service;

import br.com.stockshift.config.JwtProperties;
import br.com.stockshift.dto.auth.RegisterRequest;
import br.com.stockshift.dto.auth.RegisterResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.PermissionRepository;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.JwtTokenProvider;
import br.com.stockshift.security.PermissionCodes;
import br.com.stockshift.service.audit.AuditEventCreateRequest;
import br.com.stockshift.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

  private static final Set<String> SELLER_PERMISSION_CODES = Set.of(
      PermissionCodes.SALES_CREATE,
      PermissionCodes.SALES_READ,
      PermissionCodes.PRODUCTS_READ,
      PermissionCodes.BATCHES_READ,
      PermissionCodes.BRANDS_READ,
      PermissionCodes.CATEGORIES_READ);

  private static final Set<String> MANAGER_PERMISSION_CODES = Set.of(
      PermissionCodes.SALES_CREATE,
      PermissionCodes.SALES_READ,
      PermissionCodes.SALES_CANCEL,
      PermissionCodes.PRODUCTS_READ,
      PermissionCodes.PRODUCTS_CREATE,
      PermissionCodes.PRODUCTS_UPDATE,
      PermissionCodes.PRODUCTS_DELETE,
      PermissionCodes.PRODUCTS_ANALYZE_IMAGE,
      PermissionCodes.PRODUCT_PROMPTS_READ,
      PermissionCodes.PRODUCT_PROMPTS_CREATE,
      PermissionCodes.PRODUCT_PROMPTS_UPDATE,
      PermissionCodes.PRODUCT_PROMPTS_DELETE,
      PermissionCodes.BRANDS_READ,
      PermissionCodes.BRANDS_CREATE,
      PermissionCodes.BRANDS_UPDATE,
      PermissionCodes.BRANDS_DELETE,
      PermissionCodes.CATEGORIES_READ,
      PermissionCodes.CATEGORIES_CREATE,
      PermissionCodes.CATEGORIES_UPDATE,
      PermissionCodes.CATEGORIES_DELETE,
      PermissionCodes.BATCHES_READ,
      PermissionCodes.BATCHES_CREATE,
      PermissionCodes.BATCHES_UPDATE,
      PermissionCodes.BATCHES_DELETE,
      PermissionCodes.WAREHOUSES_READ,
      PermissionCodes.WAREHOUSES_CREATE,
      PermissionCodes.WAREHOUSES_UPDATE,
      PermissionCodes.WAREHOUSES_DELETE,
      PermissionCodes.STOCK_MOVEMENTS_READ,
      PermissionCodes.STOCK_MOVEMENTS_CREATE,
      PermissionCodes.TRANSFERS_READ,
      PermissionCodes.TRANSFERS_CREATE,
      PermissionCodes.TRANSFERS_UPDATE,
      PermissionCodes.TRANSFERS_DELETE,
      PermissionCodes.TRANSFERS_EXECUTE,
      PermissionCodes.TRANSFERS_VALIDATE,
      PermissionCodes.REPORTS_READ);

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenService refreshTokenService;
  private final JwtProperties jwtProperties;
  private final AuditService auditService;

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    log.info("Starting registration for company: {}", request.getCompanyName());

    // Validate unique email
    if (tenantRepository.findByEmail(request.getEmail()).isPresent()) {
      throw new BusinessException("Email already registered");
    }

    // Validate unique user email
    if (userRepository.findByEmail(request.getEmail()).isPresent()) {
      throw new BusinessException("Email already registered");
    }

    // Create tenant
    Tenant tenant = new Tenant();
    tenant.setBusinessName(request.getCompanyName());
    tenant.setEmail(request.getEmail());
    tenant.setIsActive(true);
    tenant = tenantRepository.save(tenant);
    log.info("Created tenant with ID: {}", tenant.getId());

    Role adminRole = createSystemRole(tenant.getId(), "ADMIN",
        "Administrator role with full access", Set.of());
    createSystemRole(tenant.getId(), "VENDEDOR",
        "Seller role with sales and stock read access", findPermissionsByCode(SELLER_PERMISSION_CODES));
    createSystemRole(tenant.getId(), "GERENTE",
        "Manager role with operational access", findPermissionsByCode(MANAGER_PERMISSION_CODES));

    // Create first admin user
    User user = new User();
    user.setTenantId(tenant.getId());
    user.setEmail(request.getEmail());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setFullName("Admin");
    user.setIsActive(true);
    user.setRoles(new HashSet<>());
    user.getRoles().add(adminRole);
    user = userRepository.save(user);
    log.info("Created admin user with ID: {} for tenant: {}", user.getId(), tenant.getId());

    // Admin users get ADMIN role and wildcard permission.
    List<String> roles = List.of("ADMIN");
    List<String> permissions = List.of("*");

    // Generate tokens
    String accessToken = jwtTokenProvider.generateAccessToken(
        user.getId(),
        user.getTenantId(),
        user.getEmail(),
        roles,
        permissions);

    RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
    auditService.record(AuditEventCreateRequest.builder()
        .tenantId(tenant.getId())
        .actorUserId(user.getId())
        .actorEmail(user.getEmail())
        .operation(AuditService.OPERATION_SECURITY)
        .action("TENANT_REGISTERED")
        .outcome(AuditService.OUTCOME_SUCCESS)
        .resourceType("TENANT")
        .resourceId(tenant.getId().toString())
        .metadata(Map.of("businessName", tenant.getBusinessName()))
        .build());

    log.info("Registration completed successfully for tenant: {}", tenant.getId());

    return RegisterResponse.builder()
        .tenantId(tenant.getId())
        .businessName(tenant.getBusinessName())
        .userId(user.getId())
        .userEmail(user.getEmail())
        .accessToken(accessToken)
        .refreshToken(refreshToken.getToken())
        .tokenType("Bearer")
        .expiresIn(jwtProperties.getAccessExpiration())
        .build();
  }

  private Role createSystemRole(UUID tenantId, String name, String description, Set<Permission> permissions) {
    Role role = new Role();
    role.setTenantId(tenantId);
    role.setName(name);
    role.setDescription(description);
    role.setIsSystemRole(true);
    role.setPermissions(new HashSet<>(permissions));
    role = roleRepository.save(role);
    log.info("Created {} role with ID: {} for tenant: {}", name, role.getId(), tenantId);
    return role;
  }

  private Set<Permission> findPermissionsByCode(Set<String> permissionCodes) {
    return permissionCodes.stream()
        .map(this::findPermissionByCode)
        .collect(Collectors.toSet());
  }

  private Permission findPermissionByCode(String code) {
    return permissionRepository.findByCodeIgnoreCase(code)
        .orElseThrow(() -> new BusinessException(
            "Missing default role permission '" + code + "', expected seeded permission code"));
  }
}
