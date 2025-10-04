package com.stockshift.backend.api.dto.user;

import com.stockshift.backend.domain.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Email(message = "Email must be valid")
    private String email;

    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String password;

    private UserRole role;

    private Boolean active;
}
