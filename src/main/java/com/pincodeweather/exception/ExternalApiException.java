package com.pincodeweather.exception;

/** An upstream provider (OpenWeather) failed or returned an unexpected response. Maps to HTTP 502. */
public class ExternalApiException extends RuntimeException {
    private final String provider;

    public ExternalApiException(String provider, String message) {
        super(provider + ": " + message);
        this.provider = provider;
    }

    public ExternalApiException(String provider, String message, Throwable cause) {
        super(provider + ": " + message, cause);
        this.provider = provider;
    }

    public String getProvider() { return provider; }
}
