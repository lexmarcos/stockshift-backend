package br.com.stockshift.service;

import br.com.stockshift.dto.auth.RegisterRequest;
import br.com.stockshift.dto.auth.RegisterResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.JwtTokenProvider;
import br.com.stockshift.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenService refreshTokenService;
  private final JwtProperties jwtProperties;

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    log.info("Starting registration for business: {}", request.getBusinessName());

    // Validate unique document
    if (tenantRepository.findByDocument(request.getDocument()).isPresent()) {
      throw new BusinessException("Document already registered");
    }

    // Validate unique tenant email
    if (tenantRepository.findByEmail(request.getTenantEmail()).isPresent()) {
      throw new BusinessException("Tenant email already registered");
    }

    // Validate unique user email
    if (userRepository.findByEmail(request.getUserEmail()).isPresent()) {
      throw new BusinessException("User email already registered");
    }

    // Create tenant
    Tenant tenant = new Tenant();
    tenant.setBusinessName(request.getBusinessName());
    tenant.setDocument(request.getDocument());
    tenant.setEmail(request.getTenantEmail());
    tenant.setPhone(request.getPhone());
    tenant.setIsActive(true);
    tenant = tenantRepository.save(tenant);
    log.info("Created tenant with ID: {}", tenant.getId());

    // Create ADMIN role for the tenant
    Role adminRole = new Role();
    adminRole.setTenantId(tenant.getId());
    adminRole.setName("ADMIN");
    adminRole.setDescription("Administrator role with full access");
    adminRole.setIsSystemRole(true);
    adminRole = roleRepository.save(adminRole);
    log.info("Created ADMIN role with ID: {} for tenant: {}", adminRole.getId(), tenant.getId());

    // Create first admin user
    User user = new User();
    user.setTenantId(tenant.getId());
    user.setEmail(request.getUserEmail());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setFullName(request.getFullName());
    user.setIsActive(true);
    user.setRoles(new HashSet<>());
    user.getRoles().add(adminRole);
    user = userRepository.save(user);
    log.info("Created admin user with ID: {} for tenant: {}", user.getId(), tenant.getId());

    // Generate tokens
    String accessToken = jwtTokenProvider.generateAccessToken(
        user.getId(),
        user.getTenantId(),
        user.getEmail());

    RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

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
}
