package com.urbanmove.fleet.infrastructure.in.web;

import com.urbanmove.fleet.domain.exception.ConcurrencyConflictException;
import com.urbanmove.fleet.domain.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalFleetExceptionHandler {

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ProblemDetail handleConcurrencyConflict(ConcurrencyConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Resource State Conflict");
        problem.setType(URI.create("https://urbanmove.local/errors/conflict"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", "CONCURRENCY_CONFLICT");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A concurrent operation prevented state change due to uniqueness constraint violation."
        );
        problem.setTitle("Resource State Conflict");
        problem.setType(URI.create("https://urbanmove.local/errors/conflict"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", "DATA_INTEGRITY_CONFLICT");
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://urbanmove.local/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", "RESOURCE_NOT_FOUND");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Request Argument");
        problem.setType(URI.create("https://urbanmove.local/errors/invalid-argument"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", "INVALID_ARGUMENT");
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Invalid State Transition");
        problem.setType(URI.create("https://urbanmove.local/errors/invalid-state"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", "INVALID_STATE_TRANSITION");
        return problem;
    }
}
