package br.com.stockshift.service.internal;

import br.com.stockshift.dto.internal.bot.BotWarehouseResponse;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotWarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public List<BotWarehouseResponse> findActiveWarehouses() {
        UUID tenantId = requireTenantId();
        return warehouseRepository.findActiveByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BotWarehouseResponse> searchActiveWarehouses(String query) {
        String sanitizedQuery = query == null ? "" : query.trim();
        if (sanitizedQuery.isBlank()) {
            return findActiveWarehouses();
        }
        UUID tenantId = requireTenantId();
        return warehouseRepository.searchActiveByTenantId(tenantId, sanitizedQuery).stream()
                .map(this::toResponse)
                .toList();
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Missing tenant context for bot warehouse request; expected STOCKSHIFT_BOT_TENANT_ID");
        }
        return tenantId;
    }

    private BotWarehouseResponse toResponse(Warehouse warehouse) {
        return BotWarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .code(warehouse.getCode())
                .city(warehouse.getCity())
                .state(warehouse.getState())
                .build();
    }
}
