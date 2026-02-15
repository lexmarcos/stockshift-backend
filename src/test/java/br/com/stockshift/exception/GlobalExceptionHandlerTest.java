package br.com.stockshift.exception;

import br.com.stockshift.security.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(rateLimitService);
        when(webRequest.getDescription(anyBoolean())).thenReturn("uri=/test/path");
    }

    @Test
    void shouldHandleOptimisticLockingFailureException() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Database conflict");

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockingFailureException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
        assertThat(response.getBody().getMessage()).contains("Another user updated the same data");
    }

    @Test
    void shouldHandlePessimisticLockingFailureException() {
        PessimisticLockingFailureException ex = new PessimisticLockingFailureException("Pessimistic lock failure");

        ResponseEntity<ErrorResponse> response = handler.handlePessimisticLockingFailureException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
        assertThat(response.getBody().getMessage()).contains("The resource is currently locked");
    }

    @Test
    void shouldHandleBadRequestException() {
        BadRequestException ex = new BadRequestException("Discrepancy report only available for transfers with discrepancies");

        ResponseEntity<ErrorResponse> response = handler.handleBadRequestException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getMessage())
                .isEqualTo("Discrepancy report only available for transfers with discrepancies");
    }

    @Test
    void shouldHandleIllegalStateException() {
        IllegalStateException ex = new IllegalStateException("Cannot transition from DRAFT to COMPLETED");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalStateException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
        assertThat(response.getBody().getMessage()).isEqualTo("Cannot transition from DRAFT to COMPLETED");
    }

    @Test
    void shouldHandleTransferCodeDataIntegrityViolation() {
        RuntimeException cause = new RuntimeException("violates constraint transfers_tenant_id_code_key");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key value", cause);

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolationException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
        assertThat(response.getBody().getMessage()).contains("Transfer code already exists");
    }
}
