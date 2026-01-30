package br.com.stockshift.mapper;

import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransferMapperTest {

    private TransferMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TransferMapper();
    }

    @Test
    void shouldMapTransferToResponse() {
        // Given
        Warehouse source = new Warehouse();
        source.setId(UUID.randomUUID());
        source.setName("Source WH");
        source.setCode("WH01");

        Warehouse destination = new Warehouse();
        destination.setId(UUID.randomUUID());
        destination.setName("Dest WH");
        destination.setCode("WH02");

        User creator = new User();
        creator.setId(UUID.randomUUID());
        creator.setFullName("John Doe");
        creator.setEmail("john@test.com");

        Transfer transfer = new Transfer();
        transfer.setId(UUID.randomUUID());
        transfer.setTransferCode("TRF-2026-00001");
        transfer.setStatus(TransferStatus.DRAFT);
        transfer.setSourceWarehouse(source);
        transfer.setDestinationWarehouse(destination);
        transfer.setCreatedBy(creator);
        transfer.setCreatedAt(LocalDateTime.now());

        // When
        TransferResponse response = mapper.toResponse(transfer, TransferRole.OUTBOUND, List.of(TransferAction.DISPATCH));

        // Then
        assertThat(response.getId()).isEqualTo(transfer.getId());
        assertThat(response.getTransferCode()).isEqualTo("TRF-2026-00001");
        assertThat(response.getStatus()).isEqualTo(TransferStatus.DRAFT);
        assertThat(response.getSourceWarehouse().getName()).isEqualTo("Source WH");
        assertThat(response.getDirection()).isEqualTo(TransferRole.OUTBOUND);
        assertThat(response.getAllowedActions()).containsExactly(TransferAction.DISPATCH);
    }

    @Test
    void shouldMapWarehouseToSummary() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setName("Test WH");
        warehouse.setCode("WH01");

        WarehouseSummary summary = mapper.toWarehouseSummary(warehouse);

        assertThat(summary.getId()).isEqualTo(warehouse.getId());
        assertThat(summary.getName()).isEqualTo("Test WH");
        assertThat(summary.getCode()).isEqualTo("WH01");
    }

    @Test
    void shouldMapUserToSummary() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Jane Doe");
        user.setEmail("jane@test.com");

        UserSummary summary = mapper.toUserSummary(user);

        assertThat(summary.getId()).isEqualTo(user.getId());
        assertThat(summary.getName()).isEqualTo("Jane Doe");
        assertThat(summary.getEmail()).isEqualTo("jane@test.com");
    }
}
