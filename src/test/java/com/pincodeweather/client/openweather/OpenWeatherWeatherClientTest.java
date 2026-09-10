package com.pincodeweather.client.openweather;

import com.pincodeweather.client.WeatherObservation;
import com.pincodeweather.config.OpenWeatherProperties;
import com.pincodeweather.exception.ApiKeyMissingException;
import com.pincodeweather.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenWeatherWeatherClientTest {

    private static final String BASE_URL = "https://weather.example/data/2.5";
    private static final String API_KEY = "test-key";
    private static final double LAT = 18.5679;
    private static final double LON = 73.9143;
    private static final String WEATHER_URL =
            BASE_URL + "/weather?lat=18.5679&lon=73.9143&units=metric&appid=" + API_KEY;

    private static final String FULL_RESPONSE = """
            {"coord":{"lon":73.9143,"lat":18.5679},
             "weather":[{"id":802,"main":"Clouds","description":"scattered clouds","icon":"03d"}],
             "base":"stations",
             "main":{"temp":27.3,"feels_like":29.1,"temp_min":26.0,"temp_max":28.5,"pressure":1010,"humidity":65,"sea_level":1010,"grnd_level":950},
             "visibility":10000,
             "wind":{"speed":3.6,"deg":250,"gust":5.1},
             "clouds":{"all":40},
             "dt":1726300000,
             "sys":{"type":2,"id":2000,"country":"IN","sunrise":1726275000,"sunset":1726319000},
             "timezone":19800,"id":1259229,"name":"Pune","cod":200}
            """;

    private MockRestServiceServer server;
    private OpenWeatherWeatherClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenWeatherWeatherClient(builder.build(), properties(API_KEY));
    }

    private static OpenWeatherProperties properties(String apiKey) {
        return new OpenWeatherProperties(apiKey, "https://geo.example/geo/1.0", BASE_URL,
                "metric", Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    @Test
    void currentWeather_mapsFullResponse() {
        server.expect(requestTo(WEATHER_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("lat", "18.5679"))
                .andExpect(queryParam("lon", "73.9143"))
                .andExpect(queryParam("units", "metric"))
                .andExpect(queryParam("appid", API_KEY))
                .andRespond(withSuccess(FULL_RESPONSE, MediaType.APPLICATION_JSON));

        WeatherObservation obs = client.currentWeather(LAT, LON);

        assertThat(obs).isEqualTo(new WeatherObservation(
                Instant.ofEpochSecond(1726300000L),
                27.3, 29.1, 26.0, 28.5, 1010, 65,
                "Clouds", "scattered clouds",
                3.6, 250, 40, 10000,
                Instant.ofEpochSecond(1726275000L),
                Instant.ofEpochSecond(1726319000L)));
        server.verify();
    }

    @Test
    void currentWeather_mapsMissingNestedObjectsToNulls() {
        server.expect(requestTo(WEATHER_URL))
                .andRespond(withSuccess("{\"dt\":1726300000,\"cod\":200}", MediaType.APPLICATION_JSON));

        WeatherObservation obs = client.currentWeather(LAT, LON);

        assertThat(obs.observedAt()).isEqualTo(Instant.ofEpochSecond(1726300000L));
        assertThat(obs).extracting(
                WeatherObservation::temperature, WeatherObservation::feelsLike,
                WeatherObservation::tempMin, WeatherObservation::tempMax,
                WeatherObservation::pressure, WeatherObservation::humidity,
                WeatherObservation::weatherMain, WeatherObservation::weatherDescription,
                WeatherObservation::windSpeed, WeatherObservation::windDegree,
                WeatherObservation::cloudiness, WeatherObservation::visibility,
                WeatherObservation::sunrise, WeatherObservation::sunset
        ).containsOnlyNulls();
        server.verify();
    }

    @Test
    void currentWeather_mapsEmptyWeatherArrayAndPartialObjectsToNulls() {
        server.expect(requestTo(WEATHER_URL))
                .andRespond(withSuccess(
                        "{\"dt\":1726300000,\"weather\":[],\"main\":{\"temp\":21.0},\"wind\":{\"speed\":1.2},\"sys\":{\"country\":\"IN\"}}",
                        MediaType.APPLICATION_JSON));

        WeatherObservation obs = client.currentWeather(LAT, LON);

        assertThat(obs.weatherMain()).isNull();
        assertThat(obs.weatherDescription()).isNull();
        assertThat(obs.temperature()).isEqualTo(21.0);
        assertThat(obs.feelsLike()).isNull();
        assertThat(obs.windSpeed()).isEqualTo(1.2);
        assertThat(obs.windDegree()).isNull();
        assertThat(obs.sunrise()).isNull();
        assertThat(obs.sunset()).isNull();
        server.verify();
    }

    @Test
    void currentWeather_throwsExternalApiExceptionWhenDtMissing() {
        server.expect(requestTo(WEATHER_URL))
                .andRespond(withSuccess("{\"main\":{\"temp\":27.3},\"cod\":200}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.currentWeather(LAT, LON))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("dt")
                .satisfies(e -> assertThat(((ExternalApiException) e).getProvider())
                        .isEqualTo("OpenWeather Current Weather"));
        server.verify();
    }

    @Test
    void currentWeather_throwsExternalApiExceptionOn401() {
        server.expect(requestTo(WEATHER_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"cod\":401,\"message\":\"Invalid API key\"}"));

        assertThatThrownBy(() -> client.currentWeather(LAT, LON))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("invalid API key")
                .hasMessageContaining("OpenWeather Current Weather");
        server.verify();
    }

    @Test
    void currentWeather_throwsExternalApiExceptionOn429() {
        server.expect(requestTo(WEATHER_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.currentWeather(LAT, LON))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("rate limit exceeded");
        server.verify();
    }

    @Test
    void currentWeather_throwsExternalApiExceptionOn404() {
        server.expect(requestTo(WEATHER_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.currentWeather(LAT, LON))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("404");
        server.verify();
    }

    @Test
    void currentWeather_throwsExternalApiExceptionOn500() {
        server.expect(requestTo(WEATHER_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.currentWeather(LAT, LON))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("500")
                .hasCauseInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void currentWeather_throwsExternalApiExceptionOnMalformedJson() {
        server.expect(requestTo(WEATHER_URL))
                .andRespond(withSuccess("<html>oops</html>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.currentWeather(LAT, LON))
                .isInstanceOf(ExternalApiException.class)
                .isNotInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void currentWeather_throwsApiKeyMissingExceptionWithoutCallingServer() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer strictServer = MockRestServiceServer.bindTo(builder).build();
        OpenWeatherWeatherClient unconfigured = new OpenWeatherWeatherClient(builder.build(), properties(null));

        assertThatThrownBy(() -> unconfigured.currentWeather(LAT, LON))
                .isInstanceOf(ApiKeyMissingException.class);
        strictServer.verify();
    }
}
