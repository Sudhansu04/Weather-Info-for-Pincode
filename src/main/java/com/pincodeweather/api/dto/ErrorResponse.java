package com.pincodeweather.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Standard error body returned by every non-2xx response.
 *
 * @param violations per-parameter validation failures; present only for 400s caused by bean validation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error body.")
public record ErrorResponse(
        @Schema(description = "When the error was produced.", example = "2020-10-15T04:05:06Z") Instant timestamp,
        @Schema(description = "HTTP status code.", example = "400") int status,
        @Schema(description = "HTTP reason phrase.", example = "Bad Request") String error,
        @Schema(description = "Human-readable explanation.", example = "pincode must be a 6-digit Indian PIN code") String message,
        @Schema(description = "Request path that produced the error.", example = "/api/v1/weather") String path,
        @Schema(description = "Validation failures per request parameter, if any.", nullable = true) List<FieldViolation> violations
) {

    /** One failed constraint on a request parameter. */
    @Schema(description = "A failed validation constraint on one request parameter.")
    public record FieldViolation(
            @Schema(description = "Request parameter name.", example = "pincode") String field,
            @Schema(description = "Why the value was rejected.", example = "pincode must be a 6-digit Indian PIN code") String message
    ) {
    }
}
