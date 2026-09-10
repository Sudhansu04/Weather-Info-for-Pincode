package com.pincodeweather.api;

import com.pincodeweather.api.dto.ErrorResponse;
import com.pincodeweather.api.dto.ErrorResponse.FieldViolation;
import com.pincodeweather.exception.ApiKeyMissingException;
import com.pincodeweather.exception.ExternalApiException;
import com.pincodeweather.exception.InvalidRequestException;
import com.pincodeweather.exception.PincodeNotFoundException;
import com.pincodeweather.exception.WeatherDataUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.method.ParameterValidationResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Translates exceptions into {@link ErrorResponse} bodies with the documented HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String FOR_DATE_PARAM = "for_date";
    static final String FOR_DATE_FORMAT_MESSAGE = "for_date must be in ISO format yyyy-MM-dd";
    static final String GENERIC_ERROR_MESSAGE = "An unexpected error occurred. Please try again later.";

    // ---- 400 ----------------------------------------------------------------------------------------------------

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    /** Bean-validation failures on {@code @Validated} controller parameters (AOP proxy path). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastNode(v.getPropertyPath()), v.getMessage()))
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, violations);
    }

    /** Bean-validation failures raised by Spring MVC's built-in method validation (non-proxy path). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String field = parameterName(result);
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                violations.add(new FieldViolation(field, error.getDefaultMessage()));
            }
        }
        violations.sort(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, violations);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        return build(HttpStatus.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = FOR_DATE_PARAM.equals(ex.getName())
                ? FOR_DATE_FORMAT_MESSAGE
                : "Parameter '" + ex.getName() + "' has an invalid value: " + ex.getValue();
        return build(HttpStatus.BAD_REQUEST, message, request, null);
    }

    // ---- 404 ----------------------------------------------------------------------------------------------------

    @ExceptionHandler({PincodeNotFoundException.class, WeatherDataUnavailableException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    // ---- 5xx ----------------------------------------------------------------------------------------------------

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApi(ExternalApiException ex, HttpServletRequest request) {
        log.warn("Upstream provider failure ({}): {}", ex.getProvider(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ApiKeyMissingException.class)
    public ResponseEntity<ErrorResponse> handleApiKeyMissing(ApiKeyMissingException ex, HttpServletRequest request) {
        log.error("OpenWeather API key is not configured");
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR_MESSAGE, request, null);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
                                                       List<FieldViolation> violations) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                violations == null || violations.isEmpty() ? null : violations);
        return ResponseEntity.status(status).body(body);
    }

    /** For a path such as {@code getWeather.pincode} returns {@code pincode}. */
    private static String lastNode(Path path) {
        String name = null;
        for (Iterator<Path.Node> it = path.iterator(); it.hasNext(); ) {
            Path.Node node = it.next();
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name == null ? path.toString() : name;
    }

    /** Prefers the {@code @RequestParam} name (e.g. {@code for_date}) over the Java parameter name. */
    private static String parameterName(ParameterValidationResult result) {
        RequestParam requestParam = result.getMethodParameter().getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.value().isBlank()) {
            return requestParam.value();
        }
        String name = result.getMethodParameter().getParameterName();
        return name != null ? name : "parameter" + result.getMethodParameter().getParameterIndex();
    }
}
