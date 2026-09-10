package com.pincodeweather.api;

import com.pincodeweather.domain.PincodeLocation;
import com.pincodeweather.domain.WeatherRecord;
import com.pincodeweather.exception.ApiKeyMissingException;
import com.pincodeweather.exception.ExternalApiException;
import com.pincodeweather.exception.InvalidRequestException;
import com.pincodeweather.exception.PincodeNotFoundException;
import com.pincodeweather.exception.WeatherDataUnavailableException;
import com.pincodeweather.service.DataSource;
import com.pincodeweather.service.WeatherLookupResult;
import com.pincodeweather.service.WeatherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WeatherController.class)
class WeatherControllerTest {

    private static final String PATH = "/api/v1/weather";
    private static final String PINCODE = "411014";
    private static final LocalDate FOR_DATE = LocalDate.of(2020, 10, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeatherService weatherService;

    private ResultActions callWeather(String pincode, String forDate) throws Exception {
        var request = get(PATH);
        if (pincode != null) {
            request.param("pincode", pincode);
        }
        if (forDate != null) {
            request.param("for_date", forDate);
        }
        return mockMvc.perform(request);
    }

    private static ResultActions expectError(ResultActions actions, int status) throws Exception {
        return actions
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.path").value(PATH));
    }

    private static WeatherLookupResult sampleResult() {
        PincodeLocation location = new PincodeLocation(PINCODE, 18.5679, 73.9143, "Pune", "IN",
                Instant.parse("2020-10-01T00:00:00Z"));
        WeatherRecord weather = WeatherRecord.builder()
                .id(7L)
                .pincode(PINCODE)
                .forDate(FOR_DATE)
                .temperature(27.3)
                .feelsLike(29.1)
                .tempMin(26.0)
                .tempMax(28.5)
                .pressure(1010)
                .humidity(65)
                .weatherMain("Clouds")
                .weatherDescription("scattered clouds")
                .windSpeed(3.6)
                .windDegree(250)
                .cloudiness(40)
                .visibility(10000)
                .sunrise(Instant.parse("2020-10-15T01:00:12Z"))
                .sunset(Instant.parse("2020-10-15T12:35:44Z"))
                .observedAt(Instant.parse("2020-10-15T04:00:00Z"))
                .fetchedAt(Instant.parse("2020-10-15T04:05:06Z"))
                .build();
        return new WeatherLookupResult(location, weather, DataSource.DATABASE, DataSource.OPENWEATHER);
    }

    @Test
    @DisplayName("200: returns the full weather payload")
    void happyPath() throws Exception {
        when(weatherService.getWeather(PINCODE, FOR_DATE)).thenReturn(sampleResult());

        callWeather(PINCODE, "2020-10-15")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.pincode").value(PINCODE))
                .andExpect(jsonPath("$.forDate").value("2020-10-15"))
                .andExpect(jsonPath("$.location.latitude").value(18.5679))
                .andExpect(jsonPath("$.location.longitude").value(73.9143))
                .andExpect(jsonPath("$.location.placeName").value("Pune"))
                .andExpect(jsonPath("$.location.country").value("IN"))
                .andExpect(jsonPath("$.location.source").value("DATABASE"))
                .andExpect(jsonPath("$.weather.temperature").value(27.3))
                .andExpect(jsonPath("$.weather.feelsLike").value(29.1))
                .andExpect(jsonPath("$.weather.tempMin").value(26.0))
                .andExpect(jsonPath("$.weather.tempMax").value(28.5))
                .andExpect(jsonPath("$.weather.pressure").value(1010))
                .andExpect(jsonPath("$.weather.humidity").value(65))
                .andExpect(jsonPath("$.weather.condition").value("Clouds"))
                .andExpect(jsonPath("$.weather.description").value("scattered clouds"))
                .andExpect(jsonPath("$.weather.windSpeed").value(3.6))
                .andExpect(jsonPath("$.weather.windDegree").value(250))
                .andExpect(jsonPath("$.weather.cloudiness").value(40))
                .andExpect(jsonPath("$.weather.visibility").value(10000))
                .andExpect(jsonPath("$.weather.sunrise").value("2020-10-15T01:00:12Z"))
                .andExpect(jsonPath("$.weather.sunset").value("2020-10-15T12:35:44Z"))
                .andExpect(jsonPath("$.weather.observedAt").value("2020-10-15T04:00:00Z"))
                .andExpect(jsonPath("$.units.temperature").value("°C"))
                .andExpect(jsonPath("$.units.windSpeed").value("m/s"))
                .andExpect(jsonPath("$.units.pressure").value("hPa"))
                .andExpect(jsonPath("$.units.visibility").value("m"))
                .andExpect(jsonPath("$.weatherSource").value("OPENWEATHER"))
                .andExpect(jsonPath("$.fetchedAt").value("2020-10-15T04:05:06Z"));

        verify(weatherService).getWeather(PINCODE, FOR_DATE);
    }

    @Nested
    @DisplayName("400: request validation")
    class Validation {

        @Test
        void missingPincode() throws Exception {
            expectError(callWeather(null, "2020-10-15"), 400)
                    .andExpect(jsonPath("$.message").value("Required parameter 'pincode' is missing"));
            verify(weatherService, never()).getWeather(anyString(), any());
        }

        @ParameterizedTest(name = "pincode \"{0}\" is rejected")
        @ValueSource(strings = {"abc", "12345", "0123456"})
        void malformedPincode(String pincode) throws Exception {
            expectError(callWeather(pincode, "2020-10-15"), 400)
                    .andExpect(jsonPath("$.violations", hasSize(1)))
                    .andExpect(jsonPath("$.violations[0].field").value("pincode"))
                    .andExpect(jsonPath("$.violations[0].message").value("pincode must be a 6-digit Indian PIN code"));
            verify(weatherService, never()).getWeather(anyString(), any());
        }

        @Test
        void missingForDate() throws Exception {
            expectError(callWeather(PINCODE, null), 400)
                    .andExpect(jsonPath("$.message").value("Required parameter 'for_date' is missing"));
            verify(weatherService, never()).getWeather(anyString(), any());
        }

        @ParameterizedTest(name = "for_date \"{0}\" is rejected")
        @ValueSource(strings = {"15-10-2020", "2020-13-45", "yesterday"})
        void malformedForDate(String forDate) throws Exception {
            expectError(callWeather(PINCODE, forDate), 400)
                    .andExpect(jsonPath("$.message").value("for_date must be in ISO format yyyy-MM-dd"))
                    .andExpect(jsonPath("$.violations").doesNotExist());
            verify(weatherService, never()).getWeather(anyString(), any());
        }
    }

    @Nested
    @DisplayName("service exceptions are mapped to HTTP statuses")
    class ServiceErrors {

        @Test
        void invalidRequestIs400() throws Exception {
            when(weatherService.getWeather(eq(PINCODE), any()))
                    .thenThrow(new InvalidRequestException("for_date must not be in the future"));

            expectError(callWeather(PINCODE, "2020-10-15"), 400)
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("for_date must not be in the future"))
                    .andExpect(jsonPath("$.violations").doesNotExist());
        }

        @Test
        void pincodeNotFoundIs404() throws Exception {
            when(weatherService.getWeather(eq(PINCODE), any()))
                    .thenThrow(new PincodeNotFoundException(PINCODE));

            expectError(callWeather(PINCODE, "2020-10-15"), 404)
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("No location found for pincode 411014"));
        }

        @Test
        void weatherDataUnavailableIs404() throws Exception {
            when(weatherService.getWeather(eq(PINCODE), any()))
                    .thenThrow(new WeatherDataUnavailableException("No stored weather for 411014 on 2020-10-15"));

            expectError(callWeather(PINCODE, "2020-10-15"), 404)
                    .andExpect(jsonPath("$.message").value("No stored weather for 411014 on 2020-10-15"));
        }

        @Test
        void externalApiFailureIs502() throws Exception {
            when(weatherService.getWeather(eq(PINCODE), any()))
                    .thenThrow(new ExternalApiException("OpenWeather", "HTTP 500 from provider"));

            expectError(callWeather(PINCODE, "2020-10-15"), 502)
                    .andExpect(jsonPath("$.error").value("Bad Gateway"))
                    .andExpect(jsonPath("$.message").value("OpenWeather: HTTP 500 from provider"));
        }

        @Test
        void apiKeyMissingIs503() throws Exception {
            when(weatherService.getWeather(eq(PINCODE), any()))
                    .thenThrow(new ApiKeyMissingException());

            expectError(callWeather(PINCODE, "2020-10-15"), 503)
                    .andExpect(jsonPath("$.error").value("Service Unavailable"))
                    .andExpect(jsonPath("$.message").value(
                            "OpenWeather API key is not configured. Set the OPENWEATHER_API_KEY environment variable."));
        }

        @Test
        void unexpectedExceptionIs500WithGenericMessage() throws Exception {
            when(weatherService.getWeather(eq(PINCODE), any()))
                    .thenThrow(new RuntimeException("database connection lost: secret details"));

            expectError(callWeather(PINCODE, "2020-10-15"), 500)
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."));
        }
    }
}
