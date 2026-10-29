package com.vishesh.effectivemenu.snapshot.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotUnavailableException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
/** Converts snapshot failures into predictable HTTP error responses. */
public class EffectiveMenuSnapshotExceptionHandler {
    @ExceptionHandler(EffectiveMenuSnapshotUnavailableException.class)
    public ResponseEntity<ApiError> handleSnapshotUnavailable(
            EffectiveMenuSnapshotUnavailableException exception,
            HttpServletRequest servletRequest) {

        int status = HttpStatus.SERVICE_UNAVAILABLE.value();
        String message = exception.getMessage();
        String path = servletRequest.getRequestURI();

        ApiError apiError = new ApiError(status, message, path);
        return ResponseEntity.status(status).body(apiError);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidArgument(
            IllegalArgumentException exception,
            HttpServletRequest servletRequest) {

        int status = HttpStatus.BAD_REQUEST.value();
        ApiError apiError = new ApiError(status, exception.getMessage(), servletRequest.getRequestURI());
        return ResponseEntity.status(status).body(apiError);
    }

}
