package br.com.stockshift.exception;

public class InvalidPriceException extends BusinessException {
    public InvalidPriceException(String message) {
        super(message);
    }
}
