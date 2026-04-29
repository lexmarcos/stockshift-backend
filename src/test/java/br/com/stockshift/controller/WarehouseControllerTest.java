package br.com.stockshift.controller;

import br.com.stockshift.dto.warehouse.ProductWithStockResponse;
import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.dto.warehouse.WarehouseResponse;
import br.com.stockshift.dto.warehouse.WarehouseStockSummaryResponse;
import br.com.stockshift.service.WarehouseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseControllerTest {

    @Mock
    private WarehouseService warehouseService;

    @Test
    void shouldWrapCrudAndSummaryResponses() {
        WarehouseController controller = new WarehouseController(warehouseService);
        UUID warehouseId = UUID.randomUUID();
        WarehouseResponse warehouse = WarehouseResponse.builder()
                .id(warehouseId)
                .name("Principal")
                .code("PRI-RE")
                .build();
        WarehouseStockSummaryResponse summary = WarehouseStockSummaryResponse.builder()
                .warehouseId(warehouseId)
                .productCount(2L)
                .batchCount(3L)
                .totalQuantity(new BigDecimal("7"))
                .build();
        when(warehouseService.create(any())).thenReturn(warehouse);
        when(warehouseService.findAll()).thenReturn(List.of(warehouse));
        when(warehouseService.getStockSummaries()).thenReturn(List.of(summary));
        when(warehouseService.findById(warehouseId)).thenReturn(warehouse);
        when(warehouseService.findActive(true)).thenReturn(List.of(warehouse));
        when(warehouseService.update(eq(warehouseId), any())).thenReturn(warehouse);

        assertThat(controller.create(request()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.findAll().getBody().getData()).singleElement().extracting("name")
                .isEqualTo("Principal");
        assertThat(controller.getStockSummaries().getBody().getData()).singleElement().extracting("batchCount")
                .isEqualTo(3L);
        assertThat(controller.findById(warehouseId).getBody().getData().getCode()).isEqualTo("PRI-RE");
        assertThat(controller.findActive(true).getBody().getData()).hasSize(1);
        assertThat(controller.update(warehouseId, request()).getBody().getSuccess()).isTrue();
        assertThat(controller.delete(warehouseId).getBody().getSuccess()).isTrue();
        verify(warehouseService).delete(warehouseId);
    }

    @Test
    void productsWithStockShouldDropUnsupportedSortFields() {
        WarehouseController controller = new WarehouseController(warehouseService);
        UUID warehouseId = UUID.randomUUID();
        ProductWithStockResponse product = ProductWithStockResponse.builder()
                .id(UUID.randomUUID())
                .name("Produto")
                .totalQuantity(4L)
                .build();
        when(warehouseService.getProductsWithStock(eq(warehouseId), eq("prod"), any()))
                .thenReturn(new PageImpl<>(List.of(product)));
        Pageable pageable = PageRequest.of(1, 25, Sort.by(
                Sort.Order.asc("name"),
                Sort.Order.desc("DROP TABLE products")
        ));

        var response = controller.getProductsWithStock(warehouseId, "prod", pageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(warehouseService).getProductsWithStock(eq(warehouseId), eq("prod"), captor.capture());
        Pageable sanitized = captor.getValue();
        assertThat(response.getBody().getData().getContent()).singleElement().extracting("name")
                .isEqualTo("Produto");
        assertThat(sanitized.getPageNumber()).isEqualTo(1);
        assertThat(sanitized.getPageSize()).isEqualTo(25);
        assertThat(sanitized.getSort()).singleElement().satisfies(order -> {
            assertThat(order.getProperty()).isEqualTo("name");
            assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
        });
    }

    @Test
    void productsWithStockShouldReturnUnsortedPageableWhenAllSortFieldsAreInvalid() {
        WarehouseController controller = new WarehouseController(warehouseService);
        UUID warehouseId = UUID.randomUUID();
        when(warehouseService.getProductsWithStock(eq(warehouseId), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of()));
        Pageable pageable = PageRequest.of(2, 10, Sort.by("invalid"));

        controller.getProductsWithStock(warehouseId, null, pageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(warehouseService).getProductsWithStock(eq(warehouseId), eq(null), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    private WarehouseRequest request() {
        return WarehouseRequest.builder()
                .name("Principal")
                .code("PRI-RE")
                .city("Recife")
                .state("PE")
                .address("Rua 1")
                .isActive(true)
                .build();
    }
}
