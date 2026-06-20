package br.com.stockshift.service.internal;

import br.com.stockshift.dto.internal.bot.BotProductSearchProjection;
import br.com.stockshift.dto.internal.bot.BotProductSearchResponse;
import br.com.stockshift.dto.internal.bot.BotProductSearchResultResponse;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.repository.BotProductSearchRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotProductSearchService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;

    private final BotProductSearchRepository botProductSearchRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public BotProductSearchResponse search(String query, UUID warehouseId, Integer requestedLimit) {
        UUID tenantId = requireTenantId();
        String sanitizedQuery = requireQuery(query);
        validateWarehouse(tenantId, warehouseId);
        int limit = sanitizeLimit(requestedLimit);
        List<BotProductSearchProjection> matches = botProductSearchRepository.searchProductsForBot(
                tenantId, warehouseId, sanitizedQuery, limit + 1);
        return toResponse(matches, limit);
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Missing tenant context for bot product search; expected STOCKSHIFT_BOT_TENANT_ID");
        }
        return tenantId;
    }

    private String requireQuery(String query) {
        String sanitizedQuery = query == null ? "" : query.trim();
        if (sanitizedQuery.isBlank()) {
            throw new BadRequestException("Invalid bot product query ''; expected non-blank name, SKU, or barcode");
        }
        return sanitizedQuery;
    }

    private void validateWarehouse(UUID tenantId, UUID warehouseId) {
        if (warehouseId == null) {
            throw new BadRequestException("Invalid warehouseId null; expected UUID for bot product search");
        }
        warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)
                .filter(warehouse -> Boolean.TRUE.equals(warehouse.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));
    }

    private int sanitizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
    }

    private BotProductSearchResponse toResponse(List<BotProductSearchProjection> matches, int limit) {
        boolean hasMore = matches.size() > limit;
        List<BotProductSearchResultResponse> results = matches.stream()
                .limit(limit)
                .map(this::toResult)
                .toList();
        return BotProductSearchResponse.builder()
                .results(results)
                .hasMore(hasMore)
                .build();
    }

    private BotProductSearchResultResponse toResult(BotProductSearchProjection projection) {
        return BotProductSearchResultResponse.builder()
                .productId(projection.getProductId())
                .name(projection.getName())
                .imageUrl(projection.getImageUrl())
                .barcode(projection.getBarcode())
                .sku(projection.getSku())
                .warehouseId(projection.getWarehouseId())
                .warehouseName(projection.getWarehouseName())
                .totalQuantity(projection.getTotalQuantity())
                .latestBatchSellingPrice(projection.getLatestBatchSellingPrice())
                .latestBatchCode(projection.getLatestBatchCode())
                .latestBatchCreatedAt(projection.getLatestBatchCreatedAt())
                .build();
    }
}
