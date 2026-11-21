package com.gym.springapp.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class BiometricMismatchException extends RuntimeException {
    public BiometricMismatchException(String message) {
        super(message);
    }
}
