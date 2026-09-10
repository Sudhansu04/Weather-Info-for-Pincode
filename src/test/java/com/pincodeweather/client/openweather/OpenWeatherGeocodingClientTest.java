package com.pincodeweather.client.openweather;

import com.pincodeweather.client.GeoLocation;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenWeatherGeocodingClientTest {

    private static final String BASE_URL = "https://geo.example/geo/1.0";
    private static final String API_KEY = "test-key";
    private static final String ZIP_URL = BASE_URL + "/zip?zip=411014,IN&appid=" + API_KEY;

    private MockRestServiceServer server;
    private OpenWeatherGeocodingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenWeatherGeocodingClient(builder.build(), properties(API_KEY));
    }

    private static OpenWeatherProperties properties(String apiKey) {
        return new OpenWeatherProperties(apiKey, BASE_URL, "https://weather.example/data/2.5",
                "metric", Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    @Test
    void lookup_mapsSuccessfulResponseToGeoLocation() {
        server.expect(requestTo(ZIP_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("zip", "411014,IN"))
                .andExpect(queryParam("appid", API_KEY))
                .andRespond(withSuccess(
                        "{\"zip\":\"411014\",\"name\":\"Pune\",\"lat\":18.5679,\"lon\":73.9143,\"country\":\"IN\"}",
                        MediaType.APPLICATION_JSON));

        Optional<GeoLocation> result = client.lookup("411014", "IN");

        assertThat(result).contains(new GeoLocation(18.5679, 73.9143, "Pune", "IN"));
        server.verify();
    }

    @Test
    void lookup_ignoresUnknownJsonProperties() {
        server.expect(requestTo(ZIP_URL))
                .andRespond(withSuccess(
                        "{\"zip\":\"411014\",\"name\":\"Pune\",\"lat\":18.5,\"lon\":73.9,\"country\":\"IN\",\"extra\":{\"x\":1}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("411014", "IN")).isPresent();
        server.verify();
    }

    @Test
    void lookup_returnsEmptyOn404() {
        server.expect(requestTo(ZIP_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"cod\":\"404\",\"message\":\"not found\"}"));

        assertThat(client.lookup("411014", "IN")).isEmpty();
        server.verify();
    }

    @Test
    void lookup_throwsExternalApiExceptionOn401() {
        server.expect(requestTo(ZIP_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"cod\":401,\"message\":\"Invalid API key\"}"));

        assertThatThrownBy(() -> client.lookup("411014", "IN"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("invalid API key")
                .satisfies(e -> assertThat(((ExternalApiException) e).getProvider()).isEqualTo("OpenWeather Geocoding"));
        server.verify();
    }

    @Test
    void lookup_throwsExternalApiExceptionOn429() {
        server.expect(requestTo(ZIP_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.lookup("411014", "IN"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("rate limit exceeded");
        server.verify();
    }

    @Test
    void lookup_throwsExternalApiExceptionOn500() {
        server.expect(requestTo(ZIP_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.lookup("411014", "IN"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("500")
                .hasCauseInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void lookup_throwsExternalApiExceptionOnMalformedJson() {
        server.expect(requestTo(ZIP_URL))
                .andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.lookup("411014", "IN"))
                .isInstanceOf(ExternalApiException.class)
                .isNotInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void lookup_throwsExternalApiExceptionWhenCoordinatesMissing() {
        server.expect(requestTo(ZIP_URL))
                .andRespond(withSuccess("{\"zip\":\"411014\",\"name\":\"Pune\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.lookup("411014", "IN"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("coordinates");
        server.verify();
    }

    @Test
    void lookup_throwsApiKeyMissingExceptionWithoutCallingServer() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer strictServer = MockRestServiceServer.bindTo(builder).build();
        OpenWeatherGeocodingClient unconfigured =
                new OpenWeatherGeocodingClient(builder.build(), properties("  "));

        assertThatThrownBy(() -> unconfigured.lookup("411014", "IN"))
                .isInstanceOf(ApiKeyMissingException.class);
        strictServer.verify(); // no expectations registered -> any request would have failed
    }
}
