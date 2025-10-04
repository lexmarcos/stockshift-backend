package com.stockshift.backend.api.dto.warehouse;

import com.stockshift.backend.domain.warehouse.WarehouseType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWarehouseRequest {

    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private WarehouseType type;

    @Size(max = 200, message = "Address must not exceed 200 characters")
    private String address;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 2, message = "State must be 2 characters")
    private String state;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;

    @Size(max = 50, message = "Country must not exceed 50 characters")
    private String country;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Size(max = 100, message = "Manager name must not exceed 100 characters")
    private String managerName;
}
