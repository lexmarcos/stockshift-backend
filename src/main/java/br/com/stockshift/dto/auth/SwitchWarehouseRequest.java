package br.com.stockshift.dto.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SwitchWarehouseRequest {

    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;
}
