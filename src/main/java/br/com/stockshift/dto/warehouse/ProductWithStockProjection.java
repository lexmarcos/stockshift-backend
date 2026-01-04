package br.com.stockshift.dto.warehouse;

import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.enums.BarcodeType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public interface ProductWithStockProjection {
    UUID getId();
    String getName();
    String getSku();
    String getBarcode();
    BarcodeType getBarcodeType();
    String getDescription();
    Category getCategory();
    Brand getBrand();
    Boolean getIsKit();
    Map<String, Object> getAttributes();
    Boolean getHasExpiration();
    Boolean getActive();
    Long getTotalQuantity();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
