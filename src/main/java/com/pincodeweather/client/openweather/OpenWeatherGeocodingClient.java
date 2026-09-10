package com.pincodeweather.client.openweather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pincodeweather.client.GeoLocation;
import com.pincodeweather.client.GeocodingClient;
import com.pincodeweather.config.OpenWeatherProperties;
import com.pincodeweather.config.RestClientConfig;
import com.pincodeweather.exception.ApiKeyMissingException;
import com.pincodeweather.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * {@link GeocodingClient} backed by the OpenWeather Geocoding API
 * (<a href="https://openweathermap.org/api/geocoding-api">zip endpoint</a>).
 */
@Component
public class OpenWeatherGeocodingClient implements GeocodingClient {

    static final String PROVIDER = "OpenWeather Geocoding";

    private static final Logger log = LoggerFactory.getLogger(OpenWeatherGeocodingClient.class);

    private final RestClient restClient;
    private final OpenWeatherProperties properties;

    public OpenWeatherGeocodingClient(@Qualifier(RestClientConfig.GEOCODING_REST_CLIENT) RestClient restClient,
                                      OpenWeatherProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Optional<GeoLocation> lookup(String pincode, String countryCode) {
        if (!properties.hasApiKey()) {
            throw new ApiKeyMissingException();
        }
        log.debug("Geocoding pincode {} ({})", pincode, countryCode);

        ZipResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/zip")
                            .queryParam("zip", pincode + "," + countryCode)
                            .queryParam("appid", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(ZipResponse.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.debug("Pincode {} ({}) not known to OpenWeather", pincode, countryCode);
                return Optional.empty();
            }
            throw OpenWeatherErrors.fromStatus(PROVIDER, e);
        } catch (RestClientException e) {
            throw OpenWeatherErrors.fromTransport(PROVIDER, e);
        }

        if (response == null) {
            throw new ExternalApiException(PROVIDER, "empty response body");
        }
        if (response.lat() == null || response.lon() == null) {
            throw new ExternalApiException(PROVIDER, "response is missing coordinates");
        }
        return Optional.of(new GeoLocation(response.lat(), response.lon(), response.name(), response.country()));
    }

    /** JSON shape of {@code GET /geo/1.0/zip}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ZipResponse(String zip, String name, Double lat, Double lon, String country) {
    }
}
