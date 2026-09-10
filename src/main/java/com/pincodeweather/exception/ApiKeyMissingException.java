package com.pincodeweather.exception;

/** The OpenWeather API key is not configured. Maps to HTTP 503. */
public class ApiKeyMissingException extends RuntimeException {
    public ApiKeyMissingException() {
        super("OpenWeather API key is not configured. Set the OPENWEATHER_API_KEY environment variable.");
    }
}
