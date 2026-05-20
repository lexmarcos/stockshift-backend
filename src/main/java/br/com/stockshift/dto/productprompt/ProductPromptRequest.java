package br.com.stockshift.dto.productprompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPromptRequest {

    @NotBlank(message = "Prompt name is required")
    @Size(max = 80, message = "Prompt name cannot exceed 80 characters")
    private String name;

    @NotBlank(message = "Prompt text is required")
    @Size(max = 4000, message = "Prompt text cannot exceed 4000 characters")
    private String prompt;
}
