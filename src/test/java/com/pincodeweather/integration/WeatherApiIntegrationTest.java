package com.pincodeweather.integration;

import com.pincodeweather.client.GeoLocation;
import com.pincodeweather.client.GeocodingClient;
import com.pincodeweather.client.WeatherClient;
import com.pincodeweather.client.WeatherObservation;
import com.pincodeweather.domain.PincodeLocationRepository;
import com.pincodeweather.domain.WeatherRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack test: real controller, service, JPA repositories, Flyway schema and H2 database.
 * Only the two external provider clients are mocked, so we can prove the RDBMS-backed call optimisation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(WeatherApiIntegrationTest.FixedClockConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:weather-it;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "openweather.api-key=test-key",
        "weather.zone=Asia/Kolkata",
        "weather.same-day-refresh-after=60m"
})
class WeatherApiIntegrationTest {

    /** 2024-03-10T06:30:00Z == 2024-03-10 12:00 IST. */
    static final Instant NOW = Instant.parse("2024-03-10T06:30:00Z");
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final LocalDate TODAY = LocalDate.ofInstant(NOW, IST);
    static final String PINCODE = "411014";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, IST);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired PincodeLocationRepository locationRepository;
    @Autowired WeatherRecordRepository weatherRepository;

    @MockitoBean GeocodingClient geocodingClient;
    @MockitoBean WeatherClient weatherClient;

    @BeforeEach
    void cleanDatabase() {
        weatherRepository.deleteAll();
        locationRepository.deleteAll();
    }

    @Test
    @DisplayName("first call geocodes + fetches weather and persists both; second call is served entirely from the DB")
    void repeatCallsAreServedFromDatabase() throws Exception {
        when(geocodingClient.lookup(PINCODE, "IN"))
                .thenReturn(Optional.of(new GeoLocation(18.5679, 73.9143, "Pune", "IN")));
        when(weatherClient.currentWeather(18.5679, 73.9143)).thenReturn(observation(27.3));

        mockMvc.perform(get("/api/v1/weather").param("pincode", PINCODE).param("for_date", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pincode").value(PINCODE))
                .andExpect(jsonPath("$.forDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.location.latitude").value(18.5679))
                .andExpect(jsonPath("$.location.longitude").value(73.9143))
                .andExpect(jsonPath("$.location.placeName").value("Pune"))
                .andExpect(jsonPath("$.location.source").value("OPENWEATHER"))
                .andExpect(jsonPath("$.weather.temperature").value(27.3))
                .andExpect(jsonPath("$.weather.condition").value("Clouds"))
                .andExpect(jsonPath("$.weatherSource").value("OPENWEATHER"));

        assertThat(locationRepository.findById(PINCODE)).isPresent();
        assertThat(weatherRepository.findByPincodeAndForDate(PINCODE, TODAY)).isPresent();

        mockMvc.perform(get("/api/v1/weather").param("pincode", PINCODE).param("for_date", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location.source").value("DATABASE"))
                .andExpect(jsonPath("$.weatherSource").value("DATABASE"))
                .andExpect(jsonPath("$.weather.temperature").value(27.3));

        verify(geocodingClient, times(1)).lookup(anyString(), anyString());
        verify(weatherClient, times(1)).currentWeather(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("a past date with nothing stored returns 404 and never calls the weather provider")
    void pastDateWithoutStoredWeatherIs404() throws Exception {
        when(geocodingClient.lookup(PINCODE, "IN"))
                .thenReturn(Optional.of(new GeoLocation(18.5679, 73.9143, "Pune", "IN")));

        mockMvc.perform(get("/api/v1/weather").param("pincode", PINCODE).param("for_date", "2020-10-15"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/weather"));

        // The pincode's coordinates are still cached for future requests.
        assertThat(locationRepository.findById(PINCODE)).isPresent();
        verify(weatherClient, never()).currentWeather(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("unknown pincode returns 404 and stores nothing")
    void unknownPincodeIs404() throws Exception {
        when(geocodingClient.lookup("999999", "IN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/weather").param("pincode", "999999").param("for_date", TODAY.toString()))
                .andExpect(status().isNotFound());

        assertThat(locationRepository.count()).isZero();
        verify(weatherClient, never()).currentWeather(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("future date is rejected with 400 before any provider call")
    void futureDateIs400() throws Exception {
        mockMvc.perform(get("/api/v1/weather").param("pincode", PINCODE).param("for_date", TODAY.plusDays(1).toString()))
                .andExpect(status().isBadRequest());

        verify(geocodingClient, never()).lookup(anyString(), anyString());
    }

    @Test
    @DisplayName("Swagger/OpenAPI documentation is exposed")
    void openApiDocsAreExposed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/weather'].get").exists());
    }

    private static WeatherObservation observation(double temp) {
        return new WeatherObservation(
                NOW.minusSeconds(120), temp, 29.1, 26.0, 28.5, 1010, 65,
                "Clouds", "scattered clouds", 3.6, 250, 40, 10000,
                NOW.minusSeconds(6 * 3600), NOW.plusSeconds(6 * 3600));
    }
}
