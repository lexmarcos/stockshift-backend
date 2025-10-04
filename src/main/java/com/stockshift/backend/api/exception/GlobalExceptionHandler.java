package com.stockshift.backend.api.exception;

import com.stockshift.backend.domain.attribute.exception.AttributeDefinitionAlreadyExistsException;
import com.stockshift.backend.domain.attribute.exception.AttributeDefinitionNotFoundException;
import com.stockshift.backend.domain.attribute.exception.AttributeValueAlreadyExistsException;
import com.stockshift.backend.domain.attribute.exception.AttributeValueNotFoundException;
import com.stockshift.backend.domain.brand.exception.BrandAlreadyExistsException;
import com.stockshift.backend.domain.brand.exception.BrandNotFoundException;
import com.stockshift.backend.domain.category.exception.CategoryAlreadyExistsException;
import com.stockshift.backend.domain.category.exception.CategoryNotFoundException;
import com.stockshift.backend.domain.category.exception.CircularCategoryReferenceException;
import com.stockshift.backend.domain.product.exception.DuplicateAttributeCombinationException;
import com.stockshift.backend.domain.product.exception.ProductAlreadyExistsException;
import com.stockshift.backend.domain.product.exception.ProductNotFoundException;
import com.stockshift.backend.domain.product.exception.ProductVariantAlreadyExistsException;
import com.stockshift.backend.domain.product.exception.ProductVariantNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Bad Credentials",
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid username or password",
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(
            UsernameNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "User Not Found",
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "User Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "User Already Exists",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(BrandNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBrandNotFound(
            BrandNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Brand Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(BrandAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleBrandAlreadyExists(
            BrandAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Brand Already Exists",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCategoryNotFound(
            CategoryNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Category Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCategoryAlreadyExists(
            CategoryAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Category Already Exists",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(CircularCategoryReferenceException.class)
    public ResponseEntity<ErrorResponse> handleCircularCategoryReference(
            CircularCategoryReferenceException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Circular Reference",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AttributeDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAttributeDefinitionNotFound(
            AttributeDefinitionNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Attribute Definition Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(AttributeDefinitionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAttributeDefinitionAlreadyExists(
            AttributeDefinitionAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Attribute Definition Already Exists",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(AttributeValueNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAttributeValueNotFound(
            AttributeValueNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Attribute Value Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(AttributeValueAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAttributeValueAlreadyExists(
            AttributeValueAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Attribute Value Already Exists",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(
            ProductNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Product Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ProductAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleProductAlreadyExists(
            ProductAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Product Already Exists",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ProductVariantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductVariantNotFound(
            ProductVariantNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Product Variant Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ProductVariantAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleProductVariantAlreadyExists(
            ProductVariantAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Product Variant Already Exists",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(DuplicateAttributeCombinationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAttributeCombination(
            DuplicateAttributeCombinationException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Duplicate Attribute Combination",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Error",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Validation Error",
                HttpStatus.BAD_REQUEST.value(),
                details,
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // New Attribute System Exception Handlers

    @ExceptionHandler(com.stockshift.backend.application.exception.InvalidAttributePairException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAttributePair(
            com.stockshift.backend.application.exception.InvalidAttributePairException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Invalid Attribute Pair",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(com.stockshift.backend.application.exception.MissingRequiredAttributeException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequiredAttribute(
            com.stockshift.backend.application.exception.MissingRequiredAttributeException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Missing Required Attribute",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(com.stockshift.backend.application.exception.InactiveAttributeException.class)
    public ResponseEntity<ErrorResponse> handleInactiveAttribute(
            com.stockshift.backend.application.exception.InactiveAttributeException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Inactive Attribute",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(com.stockshift.backend.application.exception.AttributeNotApplicableException.class)
    public ResponseEntity<ErrorResponse> handleAttributeNotApplicable(
            com.stockshift.backend.application.exception.AttributeNotApplicableException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Attribute Not Applicable",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(com.stockshift.backend.application.exception.DuplicateSkuException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSku(
            com.stockshift.backend.application.exception.DuplicateSkuException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Duplicate SKU",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(com.stockshift.backend.application.exception.DuplicateGtinException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateGtin(
            com.stockshift.backend.application.exception.DuplicateGtinException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Duplicate GTIN",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(com.stockshift.backend.application.exception.DuplicateVariantCombinationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateVariantCombination(
            com.stockshift.backend.application.exception.DuplicateVariantCombinationException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Duplicate Variant Combination",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // Warehouse Exception Handlers

    @ExceptionHandler(com.stockshift.backend.domain.warehouse.exception.WarehouseNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWarehouseNotFound(
            com.stockshift.backend.domain.warehouse.exception.WarehouseNotFoundException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Warehouse Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(com.stockshift.backend.domain.warehouse.exception.WarehouseAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleWarehouseAlreadyExists(
            com.stockshift.backend.domain.warehouse.exception.WarehouseAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Warehouse Already Exists",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getMessage(),
                request.getRequestURI(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

