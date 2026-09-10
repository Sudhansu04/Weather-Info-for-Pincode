package com.pincodeweather.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Business settings for the weather lookup service.
 */
@Validated
@ConfigurationProperties(prefix = "weather")
public record WeatherProperties(
        /** ISO 3166 alpha-2 country the pincodes belong to (used for geocoding). */
        @NotBlank String defaultCountry,
        /** Zone that defines "today" when interpreting for_date. */
        @NotNull ZoneId zone,
        /** Same-day cached weather older than this is refreshed. Past dates are immutable. */
        @NotNull Duration sameDayRefreshAfter
) {
}
