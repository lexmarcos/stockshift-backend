package br.com.stockshift.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.stockshift.config.JwtProperties;
import br.com.stockshift.dto.auth.ChangePasswordRequest;
import br.com.stockshift.dto.auth.LoginRequest;
import br.com.stockshift.dto.auth.LoginResponse;
import br.com.stockshift.dto.auth.MeResponse;
import br.com.stockshift.dto.auth.RefreshTokenResponse;
import br.com.stockshift.dto.auth.SwitchWarehouseRequest;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.UserRoleWarehouseRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.JwtTokenProvider;
import br.com.stockshift.security.PermissionCodes;
import br.com.stockshift.security.UserPrincipal;
import br.com.stockshift.security.WarehouseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final TokenDenylistService tokenDenylistService;
    private final PasswordEncoder passwordEncoder;
    private final WarehouseRepository warehouseRepository;
    private final UserRoleWarehouseRepository userRoleWarehouseRepository;
    private final PermissionResolverService permissionResolverService;
    private final WarehouseAccessService warehouseAccessService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            // Authenticate user
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()));

            // Load user details
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UnauthorizedException("User not found"));

            if (!user.getIsActive()) {
                throw new UnauthorizedException("User account is disabled");
            }

            UUID warehouseId = resolveDefaultWarehouseId(user);
            List<String> roles = extractRoles(user, warehouseId);
            List<String> permissions = extractPermissions(user, warehouseId);

            // Generate tokens
            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(),
                    user.getTenantId(),
                    warehouseId,
                    user.getEmail(),
                    roles,
                    permissions);

            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, warehouseId);

            // Update last login
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getToken())
                    .tokenType("Bearer")
                    .expiresIn(jwtProperties.getAccessExpiration())
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .mustChangePassword(user.getMustChangePassword())
                    .build();

        } catch (AuthenticationException e) {
            log.error("Authentication failed for user: {}", request.getEmail());
            throw new UnauthorizedException("Invalid email or password");
        }
    }

    @Transactional
    public RefreshTokenResponse refresh(String refreshTokenValue) {
        // Validate refresh token
        RefreshToken refreshToken = refreshTokenService.validateRefreshToken(refreshTokenValue);

        // Load user
        User user = refreshToken.getUser();
        if (user == null) {
            throw new UnauthorizedException("User not found");
        }

        if (!user.getIsActive()) {
            throw new UnauthorizedException("User account is disabled");
        }

        UUID warehouseId = refreshToken.getWarehouseId();
        if (warehouseId == null) {
            warehouseId = resolveDefaultWarehouseId(user);
        }

        if (warehouseId != null
                && !warehouseAccessService.canAccessWarehouse(user.getId(), warehouseId)
                && !hasAdminRole(user)) {
            throw new UnauthorizedException("User no longer has access to the selected warehouse");
        }

        List<String> roles = extractRoles(user, warehouseId);
        List<String> permissions = extractPermissions(user, warehouseId);

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getTenantId(),
                warehouseId,
                user.getEmail(),
                roles,
                permissions);

        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user, warehouseId);

        return RefreshTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessExpiration())
                .build();
    }

    @Transactional
    public void logout(String accessToken, String refreshTokenValue) {
        // Revoke refresh token in database
        if (refreshTokenValue != null) {
            refreshTokenService.revokeRefreshToken(refreshTokenValue);
        }

        // Add access token to denylist
        if (accessToken != null) {
            try {
                String jti = jwtTokenProvider.getJtiFromToken(accessToken);
                long ttl = jwtTokenProvider.getRemainingTtl(accessToken);
                if (ttl > 0) {
                    tokenDenylistService.addToDenylist(jti, ttl);
                    log.debug("Access token added to denylist: {}", jti);
                }
            } catch (Exception e) {
                log.warn("Failed to add access token to denylist: {}", e.getMessage());
            }
        }
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", user.getId());
    }

    @Transactional(readOnly = true)
    public MeResponse getMe() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new UnauthorizedException("User not authenticated");
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        UUID warehouseId = WarehouseContext.getWarehouseId();
        List<String> roles = extractRoles(user, warehouseId);
        List<String> permissions = extractPermissions(user, warehouseId);

        return MeResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .mustChangePassword(user.getMustChangePassword())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    private List<String> extractRoles(User user, UUID warehouseId) {
        if (warehouseId == null) {
            return user.getRoles().stream()
                    .map(role -> role.getName())
                    .distinct()
                    .sorted()
                    .toList();
        }

        Set<String> roleNames = permissionResolverService.resolveUserRoleNames(user.getId(), warehouseId);
        if (roleNames.isEmpty()) {
            return user.getRoles().stream()
                    .map(role -> role.getName())
                    .distinct()
                    .sorted()
                    .toList();
        }

        return roleNames.stream().sorted().toList();
    }

    private List<String> extractPermissions(User user, UUID warehouseId) {
        if (warehouseId == null) {
            if (hasAdminRole(user)) {
                return PermissionCodes.all();
            }
            return new ArrayList<>();
        }

        Set<String> resolved = permissionResolverService.resolveUserPermissions(user.getId(), warehouseId);
        if (resolved.isEmpty() && hasAdminRole(user)) {
            return PermissionCodes.all();
        }

        return resolved.stream()
                .sorted()
                .toList();
    }

    @Transactional
    public RefreshTokenResponse switchWarehouse(SwitchWarehouseRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new UnauthorizedException("User not authenticated");
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.getIsActive()) {
            throw new UnauthorizedException("User account is disabled");
        }

        boolean isAdmin = hasAdminRole(user);
        boolean hasAccess = warehouseAccessService.canAccessWarehouse(user.getId(), request.getWarehouseId());

        if (!hasAccess && isAdmin) {
            hasAccess = warehouseRepository.findByTenantIdAndId(user.getTenantId(), request.getWarehouseId())
                    .isPresent();
        }

        if (!hasAccess) {
            throw new ForbiddenException("User does not have access to this warehouse");
        }

        List<String> roles = extractRoles(user, request.getWarehouseId());
        List<String> permissions = extractPermissions(user, request.getWarehouseId());

        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user, request.getWarehouseId());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getTenantId(),
                request.getWarehouseId(),
                user.getEmail(),
                roles,
                permissions);

        return RefreshTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessExpiration())
                .build();
    }

    private UUID resolveDefaultWarehouseId(User user) {
        Set<UUID> warehouseIds = userRoleWarehouseRepository.findWarehouseIdsByUserId(user.getId());
        if (!warehouseIds.isEmpty()) {
            return warehouseIds.stream().sorted().findFirst().orElse(null);
        }

        if (!user.getWarehouses().isEmpty()) {
            return user.getWarehouses().stream()
                    .map(warehouse -> warehouse.getId())
                    .sorted()
                    .findFirst()
                    .orElse(null);
        }

        if (hasAdminRole(user)) {
            return warehouseRepository.findAllByTenantId(user.getTenantId()).stream()
                    .map(warehouse -> warehouse.getId())
                    .sorted()
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private boolean hasAdminRole(User user) {
        return user.getRoles().stream()
                .map(role -> role.getName())
                .anyMatch(roleName -> "ADMIN".equalsIgnoreCase(roleName) || "SUPER_ADMIN".equalsIgnoreCase(roleName));
    }
}
