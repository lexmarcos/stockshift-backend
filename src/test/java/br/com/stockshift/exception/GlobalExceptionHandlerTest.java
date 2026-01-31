package br.com.stockshift.exception;

import br.com.stockshift.security.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
