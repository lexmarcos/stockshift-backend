package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.user.CreateUserRequest;
import com.stockshift.backend.api.dto.user.UpdateUserRequest;
import com.stockshift.backend.api.dto.user.UserResponse;
import com.stockshift.backend.api.mapper.UserMapper;
import com.stockshift.backend.application.service.UserService;
import com.stockshift.backend.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toResponse(user));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<User> users = userService.getAllUsers(pageable);
        return ResponseEntity.ok(users.map(userMapper::toResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        User user = userService.updateUser(id, request);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> activateUser(@PathVariable UUID id) {
        userService.activateUser(id);
        User user = userService.getUserById(id);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }
}
