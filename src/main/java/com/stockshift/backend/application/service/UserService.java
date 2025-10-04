package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.user.CreateUserRequest;
import com.stockshift.backend.api.dto.user.UpdateUserRequest;
import com.stockshift.backend.api.exception.UserAlreadyExistsException;
import com.stockshift.backend.api.exception.UserNotFoundException;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(CreateUserRequest request) {
        // Verify if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username already exists: " + request.getUsername());
        }

        // Verify if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setActive(true);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
    }

    @Transactional(readOnly = true)
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User updateUser(UUID id, UpdateUserRequest request) {
        User user = getUserById(id);

        if (request.getEmail() != null) {
            // Check if email is already used by another user
            if (userRepository.existsByEmail(request.getEmail()) && 
                !user.getEmail().equals(request.getEmail())) {
                throw new UserAlreadyExistsException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = getUserById(id);
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public void activateUser(UUID id) {
        User user = getUserById(id);
        user.setActive(true);
        userRepository.save(user);
    }
}
