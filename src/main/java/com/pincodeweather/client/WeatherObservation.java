package com.pincodeweather.client;

import java.time.Instant;

/**
 * Provider-agnostic current-weather observation. Units follow the provider configuration (metric by default).
 */
public record WeatherObservation(
        Instant observedAt,
        Double temperature,
        Double feelsLike,
        Double tempMin,
        Double tempMax,
        Integer pressure,
        Integer humidity,
        String weatherMain,
        String weatherDescription,
        Double windSpeed,
        Integer windDegree,
        Integer cloudiness,
        Integer visibility,
        Instant sunrise,
        Instant sunset
) {
}
