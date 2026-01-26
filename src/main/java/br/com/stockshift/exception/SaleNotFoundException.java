package br.com.stockshift.exception;

public class SaleNotFoundException extends ResourceNotFoundException {
    public SaleNotFoundException(String message) {
        super(message);
    }
}
