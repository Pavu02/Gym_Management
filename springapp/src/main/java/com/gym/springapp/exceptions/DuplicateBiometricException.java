package com.gym.springapp.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateBiometricException extends RuntimeException {
    public DuplicateBiometricException(String message) {
        super(message);
    }
}
