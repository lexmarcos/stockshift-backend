package br.com.stockshift.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    // Tenant info
    @NotBlank(message = "Business name is required")
    private String businessName;
    
    @NotBlank(message = "Document (CNPJ/CPF) is required")
    private String document;
    
    @NotBlank(message = "Tenant email is required")
    @Email(message = "Invalid tenant email format")
    private String tenantEmail;
    
    private String phone;
    
    // First admin user info
    @NotBlank(message = "Full name is required")
    private String fullName;
    
    @NotBlank(message = "User email is required")
    @Email(message = "Invalid user email format")
    private String userEmail;
    
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
