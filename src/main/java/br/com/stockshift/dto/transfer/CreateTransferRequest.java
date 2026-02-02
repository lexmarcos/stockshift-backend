package br.com.stockshift.dto.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class CreateTransferRequest {

    @NotNull(message = "Destination warehouse ID is required")
    private UUID destinationWarehouseId;

    private String notes;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreateTransferItemRequest> items;
}
