package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.transfer.TransferResponse;
import com.stockshift.backend.api.mapper.StockTransferMapper;
import com.stockshift.backend.application.service.StockTransferService;
import com.stockshift.backend.domain.stock.StockTransfer;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransferControllerTest {

    @Mock
    private StockTransferService transferService;

    @Mock
    private StockTransferMapper transferMapper;

    @InjectMocks
    private StockTransferController stockTransferController;

    private Authentication authentication;
    private User currentUser;

    private StockTransfer transfer;
    private TransferResponse transferResponse;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setRole(UserRole.ADMIN);
        currentUser.setActive(true);

        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(currentUser);

        transfer = mock(StockTransfer.class);
        transferResponse = new TransferResponse();
        transferResponse.setId(UUID.randomUUID());
        transferResponse.setOccurredAt(OffsetDateTime.now());
    }

    @Test
    void confirmTransferShouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        when(transferService.confirmTransfer(id, "key", currentUser)).thenReturn(transfer);
        when(transferMapper.toResponse(transfer)).thenReturn(transferResponse);

        ResponseEntity<TransferResponse> response = stockTransferController.confirmTransfer(id, "key", authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(transferResponse);
    }

    @Test
    void cancelDraftShouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        when(transferService.cancelDraft(id, currentUser)).thenReturn(transfer);
        when(transferMapper.toResponse(transfer)).thenReturn(transferResponse);

        ResponseEntity<TransferResponse> response = stockTransferController.cancelDraft(id, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(transferResponse);
    }

    @Test
    void listTransfersShouldReturnMappedPage() {
        Page<StockTransfer> page = new PageImpl<>(List.of(transfer));
        when(transferService.listTransfers(
                any(), any(), any(), any(), any(), any(Pageable.class), any()
        )).thenReturn(page);
        when(transferMapper.toResponse(transfer)).thenReturn(transferResponse);

        Pageable pageable = PageRequest.of(0, 20);
        ResponseEntity<Page<TransferResponse>> response = stockTransferController.listTransfers(
                null, null, null, null, null, pageable, authentication
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(transferResponse);
    }

    @Test
    void getTransferShouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        when(transferService.getTransfer(id, currentUser)).thenReturn(transfer);
        when(transferMapper.toResponse(transfer)).thenReturn(transferResponse);

        ResponseEntity<TransferResponse> response = stockTransferController.getTransfer(id, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(transferResponse);
        verify(transferService).getTransfer(id, currentUser);
    }
}
