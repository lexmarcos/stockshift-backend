package br.com.stockshift.dto.transfer;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTransferRequest {

    @Valid
    private List<TransferItemRequest> items;

    private String notes;
}
