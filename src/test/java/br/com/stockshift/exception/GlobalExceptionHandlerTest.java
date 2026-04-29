package br.com.stockshift.exception;

import br.com.stockshift.security.ratelimit.RateLimitService;
import br.com.stockshift.service.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private AuditService auditService;

    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(rateLimitService, auditService);
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
    void shouldHandleCommonDomainAndAuthenticationExceptions() {
        assertThat(handler.handleResourceNotFoundException(
                new ResourceNotFoundException("Product", "id", UUID.randomUUID()), webRequest).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleBusinessException(new BusinessException("rule"), webRequest).getBody().getError())
                .isEqualTo("Business Rule Violation");
        assertThat(handler.handleInvalidFileTypeException(new InvalidFileTypeException("png only"), webRequest)
                .getBody().getError()).isEqualTo("Invalid File");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("10.0.0.1");
        when(rateLimitService.shouldRequireCaptcha(anyString())).thenReturn(true);
        ResponseEntity<ErrorResponse> unauthorized = handler.handleUnauthorizedException(
                new UnauthorizedException("login"), new ServletWebRequest(servletRequest));
        ResponseEntity<ErrorResponse> badCredentials = handler.handleBadCredentialsException(
                new BadCredentialsException("bad"), new ServletWebRequest(servletRequest));

        assertThat(unauthorized.getBody().getRequiresCaptcha()).isTrue();
        assertThat(badCredentials.getBody().getMessage()).isEqualTo("Invalid email or password");
        assertThat(badCredentials.getBody().getRequiresCaptcha()).isTrue();
    }

    @Test
    void shouldRecordAccessDeniedForForbiddenHandlers() {
        assertThat(handler.handleAccessDeniedException(new AccessDeniedException("denied"), webRequest)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleForbiddenException(new ForbiddenException("warehouse"), webRequest)
                .getBody().getMessage()).isEqualTo("warehouse");

        verify(auditService, atLeastOnce()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldHandleRequestShapeExceptions() {
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException(
                "abc", UUID.class, "id", null, new IllegalArgumentException("bad"));
        ResponseEntity<ErrorResponse> mismatchResponse = handler.handleMethodArgumentTypeMismatchException(
                mismatch, webRequest);

        ResponseEntity<ErrorResponse> missingResponse = handler.handleMissingServletRequestParameterException(
                new MissingServletRequestParameterException("order_id", "String"), webRequest);
        ResponseEntity<ErrorResponse> malformedResponse = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("bad json", org.mockito.Mockito.mock(HttpInputMessage.class)),
                webRequest);

        assertThat(mismatchResponse.getBody().getMessage()).contains("id");
        assertThat(missingResponse.getBody().getError()).isEqualTo("Missing Parameter");
        assertThat(malformedResponse.getBody().getError()).isEqualTo("Malformed Request");
    }

    @Test
    void shouldHandleConstraintViolationsAndRateLimits() {
        ConstraintViolation<?> violation = org.mockito.Mockito.mock(ConstraintViolation.class);
        Path path = org.mockito.Mockito.mock(Path.class);
        when(path.toString()).thenReturn("warehouseId");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("required");

        ResponseEntity<ErrorResponse> validation = handler.handleConstraintViolationException(
                new ConstraintViolationException(Set.of(violation)), webRequest);
        ResponseEntity<ErrorResponse> rateLimit = handler.handleRateLimitExceededException(
                new RateLimitExceededException("slow down", 30), webRequest);

        assertThat(validation.getBody().getValidationErrors()).containsEntry("warehouseId", "required");
        assertThat(rateLimit.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rateLimit.getHeaders().getFirst("Retry-After")).isEqualTo("30");
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

    @Test
    void shouldHandleOtherDataIntegrityMessagesAndUnexpectedErrors() {
        DataIntegrityViolationException warehouseCheck = new DataIntegrityViolationException(
                "violates constraint transfers_source_destination_check");
        DataIntegrityViolationException generic = new DataIntegrityViolationException("other");

        assertThat(handler.handleDataIntegrityViolationException(warehouseCheck, webRequest)
                .getBody().getMessage()).contains("Source and destination");
        assertThat(handler.handleDataIntegrityViolationException(generic, webRequest)
                .getBody().getMessage()).contains("database constraints");
        assertThat(handler.handleGlobalException(new RuntimeException("boom"), webRequest)
                .getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
