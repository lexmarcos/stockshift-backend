package br.com.stockshift.service.transfer;

import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferEvent;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.TransferEventType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.TransferEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferEventPublisherTest {

    @Mock
    private TransferEventRepository eventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Captor
    private ArgumentCaptor<TransferEvent> eventCaptor;

    private TransferEventPublisher publisher;

    private UUID tenantId;
    private UUID transferId;
    private UUID userId;
    private Transfer transfer;
    private User user;

    @BeforeEach
    void setUp() {
        publisher = new TransferEventPublisher(eventRepository, objectMapper);
        tenantId = UUID.randomUUID();
        transferId = UUID.randomUUID();
        userId = UUID.randomUUID();

        transfer = new Transfer();
        transfer.setId(transferId);
        transfer.setTenantId(tenantId);
        transfer.setStatus(TransferStatus.IN_TRANSIT);

        user = new User();
        user.setId(userId);
    }

    @Test
    void shouldPublishEventSuccessfully() throws JsonProcessingException {
        // Arrange
        TransferEventType eventType = TransferEventType.DISPATCHED;
        TransferStatus fromStatus = TransferStatus.DRAFT;
        Map<String, Object> metadata = Map.of("key", "value");
        String jsonMetadata = "{\"key\":\"value\"}";

        when(objectMapper.writeValueAsString(metadata)).thenReturn(jsonMetadata);

        // Act
        publisher.publish(transfer, eventType, fromStatus, user, metadata);

        // Assert
        verify(eventRepository).save(eventCaptor.capture());
        TransferEvent savedEvent = eventCaptor.getValue();

        assertThat(savedEvent.getTenantId()).isEqualTo(tenantId);
        assertThat(savedEvent.getTransferId()).isEqualTo(transferId);
        assertThat(savedEvent.getEventType()).isEqualTo(eventType);
        assertThat(savedEvent.getFromStatus()).isEqualTo(fromStatus);
        assertThat(savedEvent.getToStatus()).isEqualTo(TransferStatus.IN_TRANSIT);
        assertThat(savedEvent.getPerformedBy()).isEqualTo(userId);
        assertThat(savedEvent.getMetadata()).isEqualTo(jsonMetadata);
        assertThat(savedEvent.getPerformedAt()).isNotNull();
    }

    @Test
    void shouldPublishEventWithoutMetadata() {
        // Arrange
        TransferEventType eventType = TransferEventType.CREATED;

        // Act
        publisher.publish(transfer, eventType, null, user, null);

        // Assert
        verify(eventRepository).save(eventCaptor.capture());
        TransferEvent savedEvent = eventCaptor.getValue();

        assertThat(savedEvent.getMetadata()).isNull();
        assertThat(savedEvent.getFromStatus()).isNull();
    }
}
