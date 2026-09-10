package com.pincodeweather.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings for the OpenWeather Geocoding and Current Weather APIs.
 */
@Validated
@ConfigurationProperties(prefix = "openweather")
public record OpenWeatherProperties(
        /** API key. Blank means live calls will fail with a clear configuration error. */
        String apiKey,
        @NotBlank String geocodingBaseUrl,
        @NotBlank String weatherBaseUrl,
        /** "metric" (Celsius, m/s), "imperial" or "standard". */
        @NotBlank String units,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
