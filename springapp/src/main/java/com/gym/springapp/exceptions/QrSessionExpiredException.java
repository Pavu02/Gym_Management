package com.gym.springapp.exceptions;

public class QrSessionExpiredException extends RuntimeException {
    public QrSessionExpiredException(String message) {
        super(message);
    }
}
