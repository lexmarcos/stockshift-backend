package br.com.stockshift.dto.stockmovement;

import br.com.stockshift.dto.product.ProductRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStockMovementItemRequest {

  private UUID productId;

  @Valid
  private ProductRequest newProduct;

  @NotNull(message = "Quantity is required")
  @Positive(message = "Quantity must be positive")
  private BigDecimal quantity;

  @PositiveOrZero(message = "Cost price must be zero or positive")
  private Long costPrice;

  @PositiveOrZero(message = "Selling price must be zero or positive")
  private Long sellingPrice;

  @AssertTrue(message = "Item must contain either productId or newProduct, but not both")
  public boolean isProductReferenceValid() {
    boolean hasProductId = productId != null;
    boolean hasNewProduct = newProduct != null;
    return hasProductId != hasNewProduct;
  }
}
