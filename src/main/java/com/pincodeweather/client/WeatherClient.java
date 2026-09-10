package com.pincodeweather.client;

/**
 * Fetches current weather for coordinates from a provider.
 */
public interface WeatherClient {

    /**
     * @throws com.pincodeweather.exception.ExternalApiException on transport/provider failure
     * @throws com.pincodeweather.exception.ApiKeyMissingException if the provider is not configured
     */
    WeatherObservation currentWeather(double latitude, double longitude);
}
