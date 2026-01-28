package br.com.stockshift.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String email;
    private String fullName;
    private Boolean isActive;
    private Boolean mustChangePassword;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private List<String> roles;
    private List<String> warehouses;
}
