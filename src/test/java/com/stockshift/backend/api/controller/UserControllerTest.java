package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.user.CreateUserRequest;
import com.stockshift.backend.api.dto.user.UpdateUserRequest;
import com.stockshift.backend.api.dto.user.UserResponse;
import com.stockshift.backend.api.mapper.UserMapper;
import com.stockshift.backend.application.service.UserService;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserController userController;

    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("john");
        user.setRole(UserRole.ADMIN);

        userResponse = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    @Test
    void createUserShouldReturnCreatedResponse() {
        CreateUserRequest request = new CreateUserRequest();
        when(userService.createUser(request)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        ResponseEntity<UserResponse> response = userController.createUser(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(userResponse);
    }

    @Test
    void getAllUsersShouldReturnMappedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> page = new PageImpl<>(List.of(user));
        when(userService.getAllUsers(pageable)).thenReturn(page);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        ResponseEntity<Page<UserResponse>> response = userController.getAllUsers(pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(userResponse);
    }

    @Test
    void activateUserShouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        when(userService.getUserById(id)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        ResponseEntity<UserResponse> response = userController.activateUser(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(userResponse);
        verify(userService).activateUser(id);
    }
}
