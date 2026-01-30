package br.com.stockshift.dto.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransferRequest {

    @NotNull(message = "Source warehouse ID is required")
    private UUID sourceWarehouseId;

    @NotNull(message = "Destination warehouse ID is required")
    private UUID destinationWarehouseId;

    @NotEmpty(message = "Transfer must have at least one item")
    @Valid
    private List<TransferItemRequest> items;

    private String notes;
}
