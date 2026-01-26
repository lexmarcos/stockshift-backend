package br.com.stockshift.exception;

public class EmptySaleException extends BusinessException {
    public EmptySaleException(String message) {
        super(message);
    }
}
