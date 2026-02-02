package br.com.stockshift.dto.transfer;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTransferRequest {

    private String notes;

    @Valid
    private List<CreateTransferItemRequest> items;
}
