package com.gym.springapp.exceptions;

public class QrSessionConflictException extends RuntimeException {
    public QrSessionConflictException(String message) {
        super(message);
    }
}
