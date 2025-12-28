package br.com.stockshift.dto.warehouse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID warehouseId;
    private String warehouseName;
    private String batchCode;
    private Integer quantity;
    private LocalDate manufacturedDate;
    private LocalDate expirationDate;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
