package com.pincodeweather.client.openweather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pincodeweather.client.WeatherClient;
import com.pincodeweather.client.WeatherObservation;
import com.pincodeweather.config.OpenWeatherProperties;
import com.pincodeweather.config.RestClientConfig;
import com.pincodeweather.exception.ApiKeyMissingException;
import com.pincodeweather.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;

/**
 * {@link WeatherClient} backed by the OpenWeather Current Weather API
 * (<a href="https://openweathermap.org/current">/data/2.5/weather</a>).
 */
@Component
public class OpenWeatherWeatherClient implements WeatherClient {

    static final String PROVIDER = "OpenWeather Current Weather";

    private static final Logger log = LoggerFactory.getLogger(OpenWeatherWeatherClient.class);

    private final RestClient restClient;
    private final OpenWeatherProperties properties;

    public OpenWeatherWeatherClient(@Qualifier(RestClientConfig.WEATHER_REST_CLIENT) RestClient restClient,
                                    OpenWeatherProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public WeatherObservation currentWeather(double latitude, double longitude) {
        if (!properties.hasApiKey()) {
            throw new ApiKeyMissingException();
        }
        log.debug("Fetching current weather for lat={} lon={}", latitude, longitude);

        CurrentWeatherResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/weather")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("units", properties.units())
                            .queryParam("appid", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(CurrentWeatherResponse.class);
        } catch (HttpStatusCodeException e) {
            throw OpenWeatherErrors.fromStatus(PROVIDER, e);
        } catch (RestClientException e) {
            throw OpenWeatherErrors.fromTransport(PROVIDER, e);
        }

        if (response == null) {
            throw new ExternalApiException(PROVIDER, "empty response body");
        }
        return toObservation(response);
    }

    private static WeatherObservation toObservation(CurrentWeatherResponse r) {
        if (r.dt() == null) {
            throw new ExternalApiException(PROVIDER, "response is missing observation time (dt)");
        }
        Weather weather = (r.weather() == null || r.weather().isEmpty()) ? null : r.weather().get(0);
        Main main = r.main();
        Wind wind = r.wind();
        Clouds clouds = r.clouds();
        Sys sys = r.sys();

        return new WeatherObservation(
                Instant.ofEpochSecond(r.dt()),
                main == null ? null : main.temp(),
                main == null ? null : main.feelsLike(),
                main == null ? null : main.tempMin(),
                main == null ? null : main.tempMax(),
                main == null ? null : main.pressure(),
                main == null ? null : main.humidity(),
                weather == null ? null : weather.main(),
                weather == null ? null : weather.description(),
                wind == null ? null : wind.speed(),
                wind == null ? null : wind.deg(),
                clouds == null ? null : clouds.all(),
                r.visibility(),
                epochOrNull(sys == null ? null : sys.sunrise()),
                epochOrNull(sys == null ? null : sys.sunset())
        );
    }

    private static Instant epochOrNull(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }

    // --- JSON shape of GET /data/2.5/weather (only the fields we consume) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CurrentWeatherResponse(
            List<Weather> weather,
            Main main,
            Integer visibility,
            Wind wind,
            Clouds clouds,
            Long dt,
            Sys sys
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Weather(String main, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Main(
            Double temp,
            @JsonProperty("feels_like") Double feelsLike,
            @JsonProperty("temp_min") Double tempMin,
            @JsonProperty("temp_max") Double tempMax,
            Integer pressure,
            Integer humidity
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Wind(Double speed, Integer deg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Clouds(Integer all) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Sys(Long sunrise, Long sunset) {
    }
}
