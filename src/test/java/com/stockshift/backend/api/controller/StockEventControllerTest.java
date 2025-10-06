package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.stock.CreateStockEventRequest;
import com.stockshift.backend.api.dto.stock.StockEventResponse;
import com.stockshift.backend.api.mapper.StockEventMapper;
import com.stockshift.backend.application.service.StockEventService;
import com.stockshift.backend.domain.stock.StockEvent;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockEventControllerTest {

    @Mock
    private StockEventService stockEventService;

    @Mock
    private StockEventMapper stockEventMapper;

    @InjectMocks
    private StockEventController stockEventController;

    private Authentication authentication;
    private User currentUser;

    private StockEvent event;
    private StockEventResponse eventResponse;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setRole(UserRole.ADMIN);
        currentUser.setActive(true);

        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(currentUser);

        event = new StockEvent();
        event.setId(UUID.randomUUID());
        event.setType(StockEventType.INBOUND);

        eventResponse = new StockEventResponse();
        eventResponse.setId(event.getId());
        eventResponse.setType(event.getType());
    }

    @Test
    void createStockEventShouldReturnCreatedResponse() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.UTC),
                StockReasonCode.PURCHASE,
                null,
                List.of()
        );
        when(stockEventService.createStockEvent(request, null, currentUser)).thenReturn(event);
        when(stockEventMapper.toResponse(event)).thenReturn(eventResponse);

        ResponseEntity<StockEventResponse> response = stockEventController.createStockEvent(request, null, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(eventResponse);
    }

    @Test
    void listStockEventsShouldReturnMappedPage() {
        Page<StockEvent> page = new PageImpl<>(List.of(event));
        when(stockEventService.listStockEvents(
                any(), any(), any(), any(), any(), any(), any(Pageable.class), any()
        )).thenReturn(page);
        when(stockEventMapper.toResponse(event)).thenReturn(eventResponse);

        Pageable pageable = PageRequest.of(0, 20);
        ResponseEntity<Page<StockEventResponse>> response = stockEventController.listStockEvents(
                null, null, null, null, null, null, pageable, authentication
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(eventResponse);
    }

    @Test
    void getStockEventShouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        when(stockEventService.getStockEvent(id, currentUser)).thenReturn(event);
        when(stockEventMapper.toResponse(event)).thenReturn(eventResponse);

        ResponseEntity<StockEventResponse> response = stockEventController.getStockEvent(id, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(eventResponse);
        verify(stockEventService).getStockEvent(id, currentUser);
    }
}
