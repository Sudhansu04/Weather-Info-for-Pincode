package com.pincodeweather.client;

import java.util.Optional;

/**
 * Resolves a postal code to coordinates. Implementations wrap a specific provider (OpenWeather, Google Maps, ...).
 */
public interface GeocodingClient {

    /**
     * @param pincode     postal code, e.g. "411014"
     * @param countryCode ISO 3166 alpha-2 country, e.g. "IN"
     * @return the location, or empty if the provider does not know the pincode
     * @throws com.pincodeweather.exception.ExternalApiException on transport/provider failure
     * @throws com.pincodeweather.exception.ApiKeyMissingException if the provider is not configured
     */
    Optional<GeoLocation> lookup(String pincode, String countryCode);
}
