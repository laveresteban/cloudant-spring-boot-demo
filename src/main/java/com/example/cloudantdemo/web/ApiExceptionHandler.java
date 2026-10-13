package com.example.cloudantdemo.web;

import com.ibm.cloud.sdk.core.service.exception.ConflictException;
import jakarta.validation.ConstraintViolationException;
import com.ibm.cloud.sdk.core.service.exception.NotFoundException;
import com.ibm.cloud.sdk.core.service.exception.ServiceResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps Cloudant SDK errors to HTTP responses using RFC 9457 problem details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Book not found");
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Revision conflict: the book was modified. Fetch it again and retry.");
    }

    @ExceptionHandler(ServiceResponseException.class)
    public ResponseEntity<ProblemDetail> cloudantError(ServiceResponseException e) {
        // Pass through 4xx from Cloudant; anything else is reported as a bad gateway.
        int status = e.getStatusCode();
        HttpStatus mapped = (status >= 400 && status < 500)
                ? HttpStatus.valueOf(status)
                : HttpStatus.BAD_GATEWAY;
        return problem(mapped, "Cloudant error: " + e.getMessage());
    }

    /** Raised by method-level constraints such as {@code @Min} on request parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> invalidParameter(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
