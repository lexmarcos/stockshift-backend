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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
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
        List<BotProductSearchProjection> matches = searchProducts(tenantId, warehouseId, sanitizedQuery, limit + 1);
        return toResponse(matches, limit);
    }

    private List<BotProductSearchProjection> searchProducts(UUID tenantId, UUID warehouseId, String query, int limitPlusOne) {
        List<BotProductSearchProjection> fullQueryMatches = botProductSearchRepository.searchProductsForBot(
                tenantId, warehouseId, query, limitPlusOne);
        if (!fullQueryMatches.isEmpty()) {
            log.debug("Bot product search: full query '%s' matched %d products".formatted(query, fullQueryMatches.size()));
            return fullQueryMatches;
        }
        List<BotProductSearchProjection> tokenMatches = searchProductsByTokens(tenantId, warehouseId, query, limitPlusOne);
        if (!tokenMatches.isEmpty()) {
            log.debug("Bot product search: token search for '%s' matched %d products".formatted(query, tokenMatches.size()));
            return tokenMatches;
        }
        List<BotProductSearchProjection> fuzzyTokenMatches = searchProductsByFuzzyTokens(tenantId, warehouseId, query, limitPlusOne);
        if (!fuzzyTokenMatches.isEmpty()) {
            log.debug("Bot product search: fuzzy token search for '%s' matched %d products".formatted(query, fuzzyTokenMatches.size()));
            return fuzzyTokenMatches;
        }
        List<BotProductSearchProjection> fuzzyMatches = botProductSearchRepository.searchProductsFuzzyForBot(tenantId, warehouseId, query, limitPlusOne);
        log.debug("Bot product search: fuzzy search for '%s' matched %d products".formatted(query, fuzzyMatches.size()));
        return fuzzyMatches;
    }

    private List<BotProductSearchProjection> searchProductsByTokens(UUID tenantId, UUID warehouseId, String query, int limitPlusOne) {
        Map<UUID, BotProductSearchProjection> matches = new LinkedHashMap<>();
        for (String token : productSearchTokens(query)) {
            collectTokenMatches(tenantId, warehouseId, token, limitPlusOne, matches);
            if (matches.size() >= limitPlusOne) {
                break;
            }
        }
        return List.copyOf(matches.values());
    }

    private List<BotProductSearchProjection> searchProductsByFuzzyTokens(UUID tenantId, UUID warehouseId, String query, int limitPlusOne) {
        Map<UUID, BotProductSearchProjection> matches = new LinkedHashMap<>();
        for (String token : productSearchTokens(query)) {
            collectFuzzyTokenMatches(tenantId, warehouseId, token, limitPlusOne, matches);
            if (matches.size() >= limitPlusOne) {
                break;
            }
        }
        return List.copyOf(matches.values());
    }

    private void collectTokenMatches(
            UUID tenantId,
            UUID warehouseId,
            String token,
            int limitPlusOne,
            Map<UUID, BotProductSearchProjection> matches) {
        List<BotProductSearchProjection> tokenMatches = botProductSearchRepository.searchProductsForBot(
                tenantId, warehouseId, token, limitPlusOne);
        for (BotProductSearchProjection match : tokenMatches) {
            matches.putIfAbsent(match.getProductId(), match);
        }
    }

    private void collectFuzzyTokenMatches(
            UUID tenantId,
            UUID warehouseId,
            String token,
            int limitPlusOne,
            Map<UUID, BotProductSearchProjection> matches) {
        List<BotProductSearchProjection> tokenMatches = botProductSearchRepository.searchProductsFuzzyTokenForBot(
                tenantId, warehouseId, token, limitPlusOne);
        for (BotProductSearchProjection match : tokenMatches) {
            matches.putIfAbsent(match.getProductId(), match);
        }
    }

    private List<String> productSearchTokens(String query) {
        return Arrays.stream(query.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .distinct()
                .toList();
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
                .categoryName(projection.getCategoryName())
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
