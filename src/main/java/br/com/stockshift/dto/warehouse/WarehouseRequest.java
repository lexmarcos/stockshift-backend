package br.com.stockshift.dto.warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseRequest {
    @NotBlank(message = "Warehouse name is required")
    @Size(max = 255, message = "Warehouse name cannot exceed 255 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-\\.&'()]+$",
             message = "Warehouse name contains invalid characters")
    private String name;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    @Pattern(regexp = "^[\\p{L}\\s\\-']+$", message = "City contains invalid characters")
    private String city;

    @NotBlank(message = "State is required")
    @Pattern(regexp = "[A-Z]{2}", message = "State must be 2 uppercase letters")
    private String state;

    @Size(max = 500, message = "Address cannot exceed 500 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-\\.,°º'()]*$",
             message = "Address contains invalid characters")
    private String address;

    @Builder.Default
    private Boolean isActive = true;
}
