package br.com.stockshift.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserResponse {

    private UUID userId;
    private String email;
    private String fullName;
    private String temporaryPassword;
    private boolean mustChangePassword;
    private List<String> roles;
    private List<String> warehouses;
}
