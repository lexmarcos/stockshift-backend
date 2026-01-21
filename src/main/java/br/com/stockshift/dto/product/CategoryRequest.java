package br.com.stockshift.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {
    @NotBlank(message = "Category name is required")
    @Size(max = 255, message = "Category name cannot exceed 255 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-\\.&'()]+$",
             message = "Category name contains invalid characters")
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-\\.&'(),!?:;]*$",
             message = "Description contains invalid characters")
    private String description;
    private UUID parentCategoryId;
    private Map<String, Object> attributesSchema;
}
