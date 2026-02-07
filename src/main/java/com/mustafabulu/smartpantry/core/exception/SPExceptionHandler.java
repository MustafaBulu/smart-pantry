package com.mustafabulu.smartpantry.core.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
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
import static com.mustafabulu.smartpantry.core.log.LogMessages.EXCEPTION_WITH_MESSAGE;
import static com.mustafabulu.smartpantry.core.log.LogMessages.UNEXPECTED_ERROR;
import static com.mustafabulu.smartpantry.core.log.LogMessages.UNEXPECTED_TRACE;
import static com.mustafabulu.smartpantry.core.log.LogMessages.VALIDATION_FAILED;
import static com.mustafabulu.smartpantry.core.response.ResponseMessages.ALREADY_ADDED;
import static com.mustafabulu.smartpantry.core.response.ResponseMessages.ALREADY_ADDED_CODE;
import static com.mustafabulu.smartpantry.core.response.ResponseMessages.NOT_VALID;
import static com.mustafabulu.smartpantry.core.response.ResponseMessages.UNKNOWN_ERR;
import static com.mustafabulu.smartpantry.core.response.ResponseMessages.UNKNOWN_EXCEPTION;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SPExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
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
                ((ServletWebRequest) request).getRequest().getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorData);
    }

    @ExceptionHandler(SPException.class)
    protected ResponseEntity<Object> handleAppException(SPException ex, WebRequest request) {
        log.error(EXCEPTION_WITH_MESSAGE, EXCEPTION, ex.getMessage());
        log.error(EXCEPTION_WITH_MESSAGE, EXCEPTION, ex.getReason());

        String reason = Objects.toString(ex.getReason(), ex.getStatusCode().toString());
        String message = ex.getCause() != null ? ex.getCause().getMessage() : reason;

        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorData(
                LocalDateTime.now().toString(),
                ex.getStatusCode().value(),
                reason,
                message,
                ((ServletWebRequest) request).getRequest().getRequestURI()
        ));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        log.error(EXCEPTION_WITH_MESSAGE, EXCEPTION, ex.getMessage());
        log.error(EXCEPTION_WITH_MESSAGE, EXCEPTION, ex);

        return ResponseEntity.status(status).body(new ErrorData(
                LocalDateTime.now().toString(),
                status.value(),
                ex.getMessage(),
                NOT_VALID,
                ((ServletWebRequest) request).getRequest().getRequestURI()
        ));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handle(Exception ex, WebRequest request) {
        if (ex.getCause() instanceof ConstraintViolationException) {
            log.error(EXCEPTION_WITH_MESSAGE, EXCEPTION, ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorData(
                    LocalDateTime.now().toString(),
                    HttpStatus.CONFLICT.value(),
                    ALREADY_ADDED,
                    ALREADY_ADDED_CODE,
                    ((ServletWebRequest) request).getRequest().getRequestURI()
            ));
        }

        log.error(UNEXPECTED_ERROR, ex.getMessage());
        log.error(UNEXPECTED_TRACE, ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorData(
                LocalDateTime.now().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                UNKNOWN_EXCEPTION,
                UNKNOWN_ERR,
                ((ServletWebRequest) request).getRequest().getRequestURI()
        ));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode,
            @NonNull WebRequest request
    ) {
        log.error(UNEXPECTED_ERROR, ex.getMessage());
        log.error(UNEXPECTED_TRACE, ex);

        if (!HttpStatus.INTERNAL_SERVER_ERROR.equals(statusCode)) {
            return new ResponseEntity<>(body, headers, statusCode);
        }
        return ResponseEntity.status(statusCode.value()).body(new ErrorData(
                LocalDateTime.now().toString(),
                statusCode.value(),
                UNKNOWN_EXCEPTION,
                UNKNOWN_ERR,
                ((ServletWebRequest) request).getRequest().getRequestURI()
        ));
    }
}
