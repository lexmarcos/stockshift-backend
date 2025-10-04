package com.stockshift.backend.api.dto.warehouse;

import com.stockshift.backend.domain.warehouse.WarehouseType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private WarehouseType type;
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String phone;
    private String email;
    private String managerName;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
