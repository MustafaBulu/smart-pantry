package com.mustafabulu.smartpantry.common.core.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SPExceptionHandlerTest {

    @Test
    void handleAppExceptionBuildsErrorData() {
        SPExceptionHandler handler = new SPExceptionHandler();
        SPException exception = new SPException(HttpStatus.NOT_FOUND, "Not found", "NOT_FOUND_CODE");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products/1");

        var response = handler.handleAppException(exception, new ServletWebRequest(request));

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void handleConstraintViolationReturnsConflict() {
        SPExceptionHandler handler = new SPExceptionHandler();
        Exception exception = new Exception(new ConstraintViolationException("dup", Collections.emptySet()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products/1");

        var response = handler.handle(exception, new ServletWebRequest(request));

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatusCode().value());
    }
}