package br.com.stockshift.service.transfer;

import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferEvent;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.TransferEventType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.TransferEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service responsible for publishing and recording transfer lifecycle events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferEventPublisher {

    private final TransferEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records a new transfer event.
     * Use REQUIRES_NEW or ensure it's part of the same transaction depending on consistency needs.
     * Here we use default transaction participation.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void publish(
            Transfer transfer,
            TransferEventType eventType,
            TransferStatus fromStatus,
            User performedBy,
            Map<String, Object> metadata
    ) {
        String jsonMetadata = null;
        if (metadata != null && !metadata.isEmpty()) {
            try {
                jsonMetadata = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize transfer event metadata for transfer {}", transfer.getId(), e);
                // We don't want to fail the whole operation just because metadata serialization failed
                jsonMetadata = "{\"error\": \"Serialization failed\"}";
            }
        }

        TransferEvent event = TransferEvent.builder()
                .transferId(transfer.getId())
                .eventType(eventType)
                .fromStatus(fromStatus)
                .toStatus(transfer.getStatus())
                .performedBy(performedBy.getId())
                .performedAt(LocalDateTime.now())
                .metadata(jsonMetadata)
                .build();

        event.setTenantId(transfer.getTenantId());

        eventRepository.save(event);
        log.debug("Published {} event for transfer {}", eventType, transfer.getId());
    }
}
