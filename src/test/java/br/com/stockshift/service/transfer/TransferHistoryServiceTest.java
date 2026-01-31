package br.com.stockshift.service.transfer;

import br.com.stockshift.dto.transfer.TransferHistoryResponse;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferEvent;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.TransferEventType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.TransferEventRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.service.WarehouseAccessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferHistoryServiceTest {

    @Mock
    private TransferEventRepository transferEventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WarehouseAccessService warehouseAccessService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransferHistoryService transferHistoryService;

    private UUID tenantId;
    private Transfer transfer;
    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        transfer = new Transfer();
        transfer.setId(UUID.randomUUID());
        transfer.setTransferCode("TRF-001");
        transfer.setTenantId(tenantId);

        user = new User();
        user.setId(userId);
        user.setFullName("Test User");
        user.setTenantId(tenantId);
    }

    @Test
    void getHistory_ShouldReturnMappedResponse() throws JsonProcessingException {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        TransferEvent event = TransferEvent.builder()
                .transferId(transfer.getId())
                .eventType(TransferEventType.DISPATCHED)
                .fromStatus(TransferStatus.DRAFT)
                .toStatus(TransferStatus.IN_TRANSIT)
                .performedBy(userId)
                .performedAt(now)
                .metadata("{\"reason\": \"urgent\"}")
                .build();
        event.setId(UUID.randomUUID());

        when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
        when(transferEventRepository.findByTenantIdAndTransferIdOrderByPerformedAtAsc(tenantId, transfer.getId()))
                .thenReturn(List.of(event));
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));

        Map<String, Object> metadataMap = Map.of("reason", "urgent");
        when(objectMapper.readValue(event.getMetadata(), Map.class)).thenReturn(metadataMap);

        // Act
        TransferHistoryResponse response = transferHistoryService.getHistory(transfer);

        // Assert
        assertNotNull(response);
        assertEquals(transfer.getId(), response.getTransferId());
        assertEquals("TRF-001", response.getTransferCode());
        assertEquals(1, response.getEvents().size());

        var eventResponse = response.getEvents().get(0);
        assertEquals(event.getId(), eventResponse.getId());
        assertEquals(TransferEventType.DISPATCHED, eventResponse.getEventType());
        assertEquals("Test User", eventResponse.getPerformedByName());
        assertEquals(metadataMap, eventResponse.getMetadata());

        verify(transferEventRepository).findByTenantIdAndTransferIdOrderByPerformedAtAsc(tenantId, transfer.getId());
        verify(userRepository).findByTenantIdAndId(tenantId, userId);
    }

    @Test
    void getHistory_WithUnknownUser_ShouldReturnNullName() {
        // Arrange
        TransferEvent event = TransferEvent.builder()
                .transferId(transfer.getId())
                .performedBy(userId)
                .metadata(null)
                .build();

        when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
        when(transferEventRepository.findByTenantIdAndTransferIdOrderByPerformedAtAsc(tenantId, transfer.getId()))
                .thenReturn(List.of(event));
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.empty());

        // Act
        TransferHistoryResponse response = transferHistoryService.getHistory(transfer);

        // Assert
        assertEquals("Unknown User", response.getEvents().get(0).getPerformedByName());
    }
}
