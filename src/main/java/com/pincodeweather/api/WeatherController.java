package com.pincodeweather.api;

import com.pincodeweather.api.dto.ErrorResponse;
import com.pincodeweather.api.dto.WeatherResponse;
import com.pincodeweather.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST entry point for pincode weather lookups.
 *
 * <p>Syntactic validation (PIN code shape, ISO date) happens here via bean validation; semantic rules
 * (future dates, unknown pincodes, missing history) are enforced by {@link WeatherService}.
 */
@RestController
@RequestMapping("/api/v1/weather")
@Validated
@Tag(name = "Weather", description = "Weather for an Indian pincode on a given date, cached in the database.")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * Returns the weather for {@code pincode} on {@code for_date}.
     */
    @Operation(
            summary = "Get weather for a pincode on a date",
            description = """
                    Resolves the pincode to coordinates (cached per pincode in `pincode_location`) and returns the
                    weather for the requested date (cached per pincode + date in `weather_record`).

                    * `for_date` = today: served from the cache if the stored row is fresh, otherwise fetched live
                      from OpenWeather and stored.
                    * `for_date` in the past: served from the cache only; if nothing was stored on that day the
                      response is 404 because the free current-weather API has no history.
                    * `for_date` in the future: 400.

                    The `location.source` and `weatherSource` fields report whether each part came from the
                    `DATABASE` or was fetched from `OPENWEATHER` on this request.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Weather found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WeatherResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed pincode or date, missing parameter, or a future date",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pincode unknown to the geocoder, or no stored weather for a past date",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "OpenWeather failed or returned an unexpected response",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "OpenWeather API key is not configured",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public WeatherResponse getWeather(
            @Parameter(description = "6-digit Indian PIN code.", example = "411014", required = true)
            @RequestParam("pincode")
            @NotBlank
            @Pattern(regexp = "^[1-9][0-9]{5}$", message = "pincode must be a 6-digit Indian PIN code")
            String pincode,

            @Parameter(description = "Date in ISO format yyyy-MM-dd. Must not be in the future.",
                    example = "2020-10-15", required = true)
            @RequestParam("for_date")
            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate forDate) {

        return WeatherResponse.from(weatherService.getWeather(pincode, forDate));
    }
}
