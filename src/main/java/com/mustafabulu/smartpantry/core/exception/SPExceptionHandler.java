package com.mustafabulu.smartpantry.core.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.mustafabulu.smartpantry.core.log.LogMessages.EXCEPTION;
import static com.mustafabulu.smartpantry.core.log.LogMessages.UNEXPECTED_ERROR;
import static com.mustafabulu.smartpantry.core.log.LogMessages.VALIDATION_FAILED;
import static com.mustafabulu.smartpantry.core.response.ResponseMessages.ALREADY_ADDED;
import static com.mustafabulu.smartpantry.core.response.ResponseMessages.ALREADY_ADDED_CODE;
import static com.mustafabulu.smartpantry.core.response.ResponseMessages.NOT_VALID;
import static com.mustafabulu.smartpantry.core.response.ResponseMessages.UNKNOWN_ERR;
import static com.mustafabulu.smartpantry.core.response.ResponseMessages.UNKNOWN_EXCEPTION;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@NullMarked
public class SPExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        log.error(VALIDATION_FAILED, EXCEPTION, ex.getMessage());

        String validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorData errorData = new ErrorData(
                LocalDateTime.now().toString(),
                HttpStatus.BAD_REQUEST.value(),
                NOT_VALID,
                validationErrors,
                getRequestUri(request)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorData);
    }

    @ExceptionHandler(SPException.class)
    protected ResponseEntity<Object> handleAppException(SPException ex, WebRequest request) {
        log.error(
                "{} status={}, message={}, reason={}",
                EXCEPTION,
                ex.getStatusCode(),
                ex.getMessage(),
                ex.getReason()
        );

        String reason = Objects.toString(ex.getReason(), ex.getStatusCode().toString());
        String errorCode = ex.getCause() != null ? ex.getCause().getMessage() : ex.getStatusCode().toString();

        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorData(
                LocalDateTime.now().toString(),
                ex.getStatusCode().value(),
                errorCode,
                reason,
                getRequestUri(request)
        ));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        log.error("{} missing servlet request parameter: {}", EXCEPTION, ex.getMessage(), ex);

        return ResponseEntity.status(status).body(new ErrorData(
                LocalDateTime.now().toString(),
                status.value(),
                ex.getMessage(),
                NOT_VALID,
                getRequestUri(request)
        ));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handle(Exception ex, WebRequest request) {
        if (ex.getCause() instanceof ConstraintViolationException) {
            log.error("{} conflict caused by constraint violation: {}", EXCEPTION, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorData(
                    LocalDateTime.now().toString(),
                    HttpStatus.CONFLICT.value(),
                    ALREADY_ADDED,
                    ALREADY_ADDED_CODE,
                    getRequestUri(request)
            ));
        }

        log.error(UNEXPECTED_ERROR, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorData(
                LocalDateTime.now().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                UNKNOWN_EXCEPTION,
                UNKNOWN_ERR,
                getRequestUri(request)
        ));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception ex,
            @Nullable Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode,
            @NonNull WebRequest request
    ) {
        log.error(UNEXPECTED_ERROR, ex.getMessage(), ex);

        if (!HttpStatus.INTERNAL_SERVER_ERROR.equals(statusCode)) {
            return new ResponseEntity<>(body, headers, statusCode);
        }
        return ResponseEntity.status(statusCode.value()).body(new ErrorData(
                LocalDateTime.now().toString(),
                statusCode.value(),
                UNKNOWN_EXCEPTION,
                UNKNOWN_ERR,
                getRequestUri(request)
        ));
    }

    private static String getRequestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return "";
    }
}
