package br.com.stockshift.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private Boolean isActive;

    @NotEmpty(message = "At least one role is required")
    private Set<UUID> roleIds;

    @NotEmpty(message = "At least one warehouse is required")
    private Set<UUID> warehouseIds;
}
