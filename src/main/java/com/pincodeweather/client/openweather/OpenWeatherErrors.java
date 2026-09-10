package com.pincodeweather.client.openweather;

import com.pincodeweather.exception.ExternalApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

/**
 * Translates Spring {@link RestClientException}s from OpenWeather calls into {@link ExternalApiException}s
 * so that no transport-level exception type leaks out of the client package.
 */
final class OpenWeatherErrors {

    private OpenWeatherErrors() {
    }

    static ExternalApiException fromStatus(String provider, HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return new ExternalApiException(provider, "invalid API key", e);
        }
        if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            return new ExternalApiException(provider, "rate limit exceeded", e);
        }
        if (e.getStatusCode().is5xxServerError()) {
            return new ExternalApiException(provider, "server error (HTTP " + status + ")", e);
        }
        return new ExternalApiException(provider, "unexpected response (HTTP " + status + ")", e);
    }

    static ExternalApiException fromTransport(String provider, RestClientException e) {
        return new ExternalApiException(provider, "request failed: " + e.getMessage(), e);
    }
}
