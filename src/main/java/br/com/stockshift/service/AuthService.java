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
import br.com.stockshift.dto.auth.RefreshTokenRequest;
import br.com.stockshift.dto.auth.RefreshTokenResponse;
import br.com.stockshift.dto.auth.SwitchWarehouseRequest;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.security.JwtTokenProvider;
import br.com.stockshift.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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

            // Extract roles and permissions from user
            List<String> roles = extractRoles(user);
            List<String> permissions = extractPermissions(user);

            // Generate tokens
            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(),
                    user.getTenantId(),
                    user.getEmail(),
                    roles,
                    permissions);

            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

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

        // Preserve warehouseId from old token
        UUID warehouseId = refreshToken.getWarehouseId();

        // Extract roles and permissions from user
        List<String> roles = extractRoles(user);
        List<String> permissions = extractPermissions(user);

        // Generate new access token with warehouseId preserved
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getTenantId(),
                warehouseId,
                user.getEmail(),
                roles,
                permissions);

        // Rotate refresh token - create new one with warehouseId preserved
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

        List<String> roles = extractRoles(user);
        List<String> permissions = extractPermissions(user);

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

    private List<String> extractRoles(User user) {
        return user.getRoles().stream()
                .map(role -> role.getName())
                .sorted()
                .toList();
    }

    private List<String> extractPermissions(User user) {
        // Check if user has ADMIN role
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));

        if (isAdmin) {
            return List.of("*");
        }

        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> String.format("%s:%s:%s",
                        permission.getResource().name(),
                        permission.getAction().name(),
                        permission.getScope().name()))
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional
    public String switchWarehouse(SwitchWarehouseRequest request) {
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

        // Check if user is admin (has full access)
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));

        boolean hasAccess;
        if (isAdmin) {
            // Admin can access any warehouse in their tenant
            hasAccess = warehouseRepository.findByTenantIdAndId(user.getTenantId(), request.getWarehouseId())
                    .isPresent();
        } else {
            // Regular users must have explicit warehouse assignment
            hasAccess = user.getWarehouses().stream()
                    .anyMatch(warehouse -> warehouse.getId().equals(request.getWarehouseId()));
        }

        if (!hasAccess) {
            throw new UnauthorizedException("User does not have access to this warehouse");
        }

        // Extract roles and permissions
        List<String> roles = extractRoles(user);
        List<String> permissions = extractPermissions(user);

        // Create new refresh token with warehouseId
        refreshTokenService.createRefreshToken(user, request.getWarehouseId());

        // Generate new access token with warehouseId
        return jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getTenantId(),
                request.getWarehouseId(),
                user.getEmail(),
                roles,
                permissions);
    }
}
