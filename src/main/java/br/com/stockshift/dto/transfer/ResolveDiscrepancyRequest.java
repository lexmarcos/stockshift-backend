package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.DiscrepancyResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveDiscrepancyRequest {

    @NotNull(message = "Resolution is required")
    private DiscrepancyResolution resolution;

    @NotBlank(message = "Justification is required")
    private String justification;
}
