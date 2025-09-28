package ${groupId}.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Global exception handler for centralized error management across the application.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandlerController implements ErrorController {

    /**
     * Creates a standardized error response with additional context.
     *
     * @param title           Error title
     * @param ex              The exception
     * @param httpStatusCode  HTTP status code
     * @param message         Error message
     * @param request         Optional HTTP request
     * @return ResponseEntity with ErrorResponse
     */
    private ResponseEntity<ErrorResponse> createResponseEntityError(
            String title,
            Exception ex,
            HttpStatusCode httpStatusCode,
            String message,
            HttpServletRequest request
    ) {
        ErrorResponse.Builder builder = ErrorResponse.builder(ex, httpStatusCode, message)
                .title(title)
                .property("timestamp", LocalDateTime.now());

        Optional.ofNullable(request).ifPresent(req -> {
            builder.instance(URI.create(req.getRequestURL().toString()));
            builder.property("path", req.getRequestURI());
        });

        log.error("Error occurred: {}", message, ex);
        return ResponseEntity.status(httpStatusCode).body(builder.build());
    }

    /**
     * Overloaded method for creating error response without request context.
     */
    private ResponseEntity<ErrorResponse> createResponseEntityError(
            String title, 
            Exception ex, 
            HttpStatusCode httpStatusCode, 
            String message
    ) {
        return createResponseEntityError(title, ex, httpStatusCode, message, null);
    }

    /**
     * Handles generic errors from servlet context.
     */
    @GetMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        Throwable throwable = (Throwable) request.getAttribute("jakarta.servlet.error.exception");
        
        Exception exception = throwable instanceof Exception ex 
            ? ex 
            : new Exception("Unexpected server error occurred", throwable);
        
        return createResponseEntityError(
            "Internal Server Error",
            exception,
            HttpStatus.INTERNAL_SERVER_ERROR,
            Optional.ofNullable(exception.getMessage())
                .orElse("Unexpected error occurred."),
            request
        );
    }

    /**
     * Catch-all handler for unhandled exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return createResponseEntityError(
            "Internal Server Error",
            ex,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred: " + ex.getMessage()
        );
    }

    /**
     * Handles HTTP client errors with specialized processing.
     */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleHttpClientErrorException(
            HttpServletRequest req, 
            HttpClientErrorException ex
    ) {
        if (AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class) != null) {
            throw ex;
        }
        
        return createResponseEntityError(
            "External Service Error",
            ex,
            ex.getStatusCode(),
            "Error calling URL: " + req.getRequestURL() + " - " + ex.getMessage(),
            req
        );
    }

    /**
     * Handles data integrity violation exceptions.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex
    ) {
        String errorMessage = Optional.ofNullable(ex.getRootCause())
            .map(Throwable::getMessage)
            .orElse(ex.getMessage());
        
        return createResponseEntityError(
            "Data Integrity Error",
            ex,
            HttpStatus.CONFLICT,
            "Data integrity constraint violation: " + errorMessage
        );
    }

    /**
     * Handles resource not found exceptions.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElementException(
            HttpServletRequest request, 
            NoSuchElementException ex
    ) {
        return createResponseEntityError(
            "Resource Not Found",
            ex,
            HttpStatus.NOT_FOUND,
            "Could not find the requested resource: " + ex.getMessage(),
            request
        );
    }

}
