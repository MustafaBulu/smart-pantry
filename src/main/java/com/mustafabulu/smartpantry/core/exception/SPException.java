package com.mustafabulu.smartpantry.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@SuppressWarnings("java:S110")
public class SPException extends ResponseStatusException {
    public SPException(HttpStatus status, String message, String reason) {
        super(status, message, new Exception(reason));
    }
}
