package br.com.stockshift.dto.brand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 255, message = "Nome não pode exceder 255 caracteres")
    private String name;

    @Size(max = 500, message = "URL do logo não pode exceder 500 caracteres")
    private String logoUrl;
}
