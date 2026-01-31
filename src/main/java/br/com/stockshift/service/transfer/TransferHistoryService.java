package br.com.stockshift.service.transfer;

import br.com.stockshift.dto.transfer.TransferEventResponse;
import br.com.stockshift.dto.transfer.TransferHistoryResponse;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferEvent;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.TransferEventRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.service.WarehouseAccessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferHistoryService {

    private final TransferEventRepository transferEventRepository;
    private final UserRepository userRepository;
    private final WarehouseAccessService warehouseAccessService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TransferHistoryResponse getHistory(Transfer transfer) {
        UUID tenantId = warehouseAccessService.getTenantId();

        List<TransferEvent> events = transferEventRepository
                .findByTenantIdAndTransferIdOrderByPerformedAtAsc(tenantId, transfer.getId());

        List<TransferEventResponse> eventResponses = events.stream()
                .map(event -> mapToResponse(event, tenantId))
                .collect(Collectors.toList());

        return TransferHistoryResponse.builder()
                .transferId(transfer.getId())
                .transferCode(transfer.getTransferCode())
                .events(eventResponses)
                .build();
    }

    private TransferEventResponse mapToResponse(TransferEvent event, UUID tenantId) {
        String userName = userRepository.findByTenantIdAndId(tenantId, event.getPerformedBy())
                .map(User::getFullName)
                .orElse("Unknown User");

        Map<String, Object> metadata = null;
        if (event.getMetadata() != null && !event.getMetadata().isBlank()) {
            try {
                metadata = objectMapper.readValue(event.getMetadata(), Map.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse metadata for event {}", event.getId(), e);
            }
        }

        return TransferEventResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .performedBy(event.getPerformedBy())
                .performedByName(userName)
                .performedAt(event.getPerformedAt())
                .metadata(metadata)
                .build();
    }
}
