package br.com.stockshift.dto.auth;

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
public class MeResponse {
    private UUID id;
    private UUID tenantId;
    private String email;
    private String fullName;
    private Boolean mustChangePassword;
    private List<String> roles;
    private List<String> permissions;
}
