package com.pincodeweather.exception;

/** No stored weather exists for a past date and the provider cannot supply it. Maps to HTTP 404. */
public class WeatherDataUnavailableException extends RuntimeException {
    public WeatherDataUnavailableException(String message) {
        super(message);
    }
}
