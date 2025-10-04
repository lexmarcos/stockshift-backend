package com.stockshift.backend.api.dto.attribute;

import com.stockshift.backend.domain.attribute.AttributeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CreateAttributeDefinitionRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Code is required")
    @Size(max = 100, message = "Code must not exceed 100 characters")
    private String code;

    @NotNull(message = "Type is required")
    private AttributeType type;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Boolean isVariantDefining = true;

    private Boolean isRequired = false;

    private List<UUID> applicableCategoryIds = new ArrayList<>();

    private Integer sortOrder = 0;
}
