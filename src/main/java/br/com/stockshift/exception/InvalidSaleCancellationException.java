package br.com.stockshift.exception;

public class InvalidSaleCancellationException extends BusinessException {
    public InvalidSaleCancellationException(String message) {
        super(message);
    }
}
