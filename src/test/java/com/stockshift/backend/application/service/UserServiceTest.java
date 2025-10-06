package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.user.CreateUserRequest;
import com.stockshift.backend.api.dto.user.UpdateUserRequest;
import com.stockshift.backend.api.exception.UserAlreadyExistsException;
import com.stockshift.backend.api.exception.UserNotFoundException;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import com.stockshift.backend.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private CreateUserRequest createUserRequest;
    private UpdateUserRequest updateUserRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");
        testUser.setRole(UserRole.MANAGER);
        testUser.setActive(true);

        createUserRequest = new CreateUserRequest(
                "newuser",
                "new@example.com",
                "password123",
                UserRole.SELLER
        );

        updateUserRequest = new UpdateUserRequest();
        updateUserRequest.setEmail("updated@example.com");
        updateUserRequest.setPassword("newPassword123");
        updateUserRequest.setRole(UserRole.ADMIN);
    }

    @Test
    void shouldCreateUserSuccessfully() {
        // Given
        when(userRepository.existsByUsername(createUserRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(createUserRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(createUserRequest.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        User createdUser = userService.createUser(createUserRequest);

        // Then
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.getId()).isNotNull();
        verify(userRepository).existsByUsername(createUserRequest.getUsername());
        verify(userRepository).existsByEmail(createUserRequest.getEmail());
        verify(passwordEncoder).encode(createUserRequest.getPassword());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenUsernameAlreadyExists() {
        // Given
        when(userRepository.existsByUsername(createUserRequest.getUsername())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> userService.createUser(createUserRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Username already exists");

        verify(userRepository).existsByUsername(createUserRequest.getUsername());
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        // Given
        when(userRepository.existsByUsername(createUserRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(createUserRequest.getEmail())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> userService.createUser(createUserRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Email already exists");

        verify(userRepository).existsByUsername(createUserRequest.getUsername());
        verify(userRepository).existsByEmail(createUserRequest.getEmail());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldGetUserByIdSuccessfully() {
        // Given
        UUID userId = testUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        User foundUser = userService.getUserById(userId);

        // Then
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getId()).isEqualTo(userId);
        assertThat(foundUser.getUsername()).isEqualTo(testUser.getUsername());
        verify(userRepository).findById(userId);
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundById() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found with id");

        verify(userRepository).findById(userId);
    }

    @Test
    void shouldGetUserByUsernameSuccessfully() {
        // Given
        String username = testUser.getUsername();
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // When
        User foundUser = userService.getUserByUsername(username);

        // Then
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getUsername()).isEqualTo(username);
        verify(userRepository).findByUsername(username);
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundByUsername() {
        // Given
        String username = "nonexistent";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> userService.getUserByUsername(username))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found with username");

        verify(userRepository).findByUsername(username);
    }

    @Test
    void shouldGetAllUsersSuccessfully() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(testUser));
        when(userRepository.findAll(pageable)).thenReturn(userPage);

        // When
        Page<User> result = userService.getAllUsers(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(testUser);
        verify(userRepository).findAll(pageable);
    }

    @Test
    void shouldUpdateUserSuccessfully() {
        // Given
        UUID userId = testUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail(updateUserRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(updateUserRequest.getPassword())).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        User updatedUser = userService.updateUser(userId, updateUserRequest);

        // Then
        assertThat(updatedUser).isNotNull();
        verify(userRepository).findById(userId);
        verify(userRepository).existsByEmail(updateUserRequest.getEmail());
        verify(passwordEncoder).encode(updateUserRequest.getPassword());
        verify(userRepository).save(testUser);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingWithExistingEmail() {
        // Given
        UUID userId = testUser.getId();
        testUser.setEmail("old@example.com");
        updateUserRequest.setEmail("existing@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail(updateUserRequest.getEmail())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> userService.updateUser(userId, updateUserRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Email already exists");

        verify(userRepository).findById(userId);
        verify(userRepository).existsByEmail(updateUserRequest.getEmail());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldUpdateUserWithSameEmail() {
        // Given
        UUID userId = testUser.getId();
        updateUserRequest.setEmail(testUser.getEmail()); // Same email

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail(updateUserRequest.getEmail())).thenReturn(true);
        when(passwordEncoder.encode(updateUserRequest.getPassword())).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        User updatedUser = userService.updateUser(userId, updateUserRequest);

        // Then
        assertThat(updatedUser).isNotNull();
        verify(userRepository).findById(userId);
        verify(userRepository).save(testUser);
    }

    @Test
    void shouldUpdateUserOnlyEmail() {
        // Given
        UUID userId = testUser.getId();
        UpdateUserRequest emailOnlyRequest = new UpdateUserRequest();
        emailOnlyRequest.setEmail("newemail@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail(emailOnlyRequest.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        User updatedUser = userService.updateUser(userId, emailOnlyRequest);

        // Then
        assertThat(updatedUser).isNotNull();
        verify(userRepository).findById(userId);
        verify(userRepository).existsByEmail(emailOnlyRequest.getEmail());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository).save(testUser);
    }

    @Test
    void shouldDeleteUserSuccessfully() {
        // Given
        UUID userId = testUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        userService.deleteUser(userId);

        // Then
        verify(userRepository).findById(userId);
        verify(userRepository).save(testUser);
        assertThat(testUser.getActive()).isFalse();
    }

    @Test
    void shouldActivateUserSuccessfully() {
        // Given
        UUID userId = testUser.getId();
        testUser.setActive(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        userService.activateUser(userId);

        // Then
        verify(userRepository).findById(userId);
        verify(userRepository).save(testUser);
        assertThat(testUser.getActive()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistentUser() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found with id");

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenActivatingNonExistentUser() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> userService.activateUser(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found with id");

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any(User.class));
    }
}
