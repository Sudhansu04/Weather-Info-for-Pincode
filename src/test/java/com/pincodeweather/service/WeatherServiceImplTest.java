package com.pincodeweather.service;

import com.pincodeweather.client.GeoLocation;
import com.pincodeweather.client.GeocodingClient;
import com.pincodeweather.client.WeatherClient;
import com.pincodeweather.client.WeatherObservation;
import com.pincodeweather.config.WeatherProperties;
import com.pincodeweather.domain.PincodeLocation;
import com.pincodeweather.domain.PincodeLocationRepository;
import com.pincodeweather.domain.WeatherRecord;
import com.pincodeweather.domain.WeatherRecordRepository;
import com.pincodeweather.exception.InvalidRequestException;
import com.pincodeweather.exception.PincodeNotFoundException;
import com.pincodeweather.exception.WeatherDataUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link WeatherServiceImpl} with mocked repositories and clients and a fixed clock.
 */
@ExtendWith(MockitoExtension.class)
class WeatherServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = ZonedDateTime.of(2026, 9, 5, 10, 0, 0, 0, ZONE).toInstant();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final Duration TTL = Duration.ofMinutes(60);
    private static final String PINCODE = "411014";
    private static final double LAT = 18.5679;
    private static final double LON = 73.9143;

    @Mock private PincodeLocationRepository pincodeLocationRepository;
    @Mock private WeatherRecordRepository weatherRecordRepository;
    @Mock private GeocodingClient geocodingClient;
    @Mock private WeatherClient weatherClient;

    private WeatherServiceImpl service;

    @BeforeEach
    void setUp() {
        WeatherProperties properties = new WeatherProperties("IN", ZONE, TTL);
        Clock clock = Clock.fixed(NOW, ZONE);
        service = new WeatherServiceImpl(pincodeLocationRepository, weatherRecordRepository,
                geocodingClient, weatherClient, properties, clock);
    }

    // ---------------------------------------------------------------- date validation

    @Test
    void futureDateIsRejectedBeforeTouchingAnyCollaborator() {
        assertThatThrownBy(() -> service.getWeather(PINCODE, TOMORROW))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("for_date cannot be in the future");

        verifyNoInteractions(pincodeLocationRepository, weatherRecordRepository, geocodingClient, weatherClient);
    }

    // ---------------------------------------------------------------- location resolution

    @Test
    void unknownPincodeThrowsNotFoundAndSavesNothing() {
        when(pincodeLocationRepository.findById(PINCODE)).thenReturn(Optional.empty());
        when(geocodingClient.lookup(PINCODE, "IN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWeather(PINCODE, TODAY))
                .isInstanceOf(PincodeNotFoundException.class)
                .hasMessageContaining(PINCODE);

        verify(pincodeLocationRepository, never()).save(any());
        verifyNoInteractions(weatherRecordRepository, weatherClient);
    }

    @Test
    void locationCacheHitSkipsGeocoding() {
        PincodeLocation cached = storedLocation();
        when(pincodeLocationRepository.findById(PINCODE)).thenReturn(Optional.of(cached));
        when(weatherRecordRepository.findByPincodeAndForDate(PINCODE, TODAY)).thenReturn(Optional.of(freshRecord()));

        WeatherLookupResult result = service.getWeather(PINCODE, TODAY);

        assertThat(result.location()).isSameAs(cached);
        assertThat(result.locationSource()).isEqualTo(DataSource.DATABASE);
        verifyNoInteractions(geocodingClient);
        verify(pincodeLocationRepository, never()).save(any());
    }

    @Test
    void locationCacheMissGeocodesAndPersistsWithMappedFields() {
        when(pincodeLocationRepository.findById(PINCODE)).thenReturn(Optional.empty());
        when(geocodingClient.lookup(PINCODE, "IN"))
                .thenReturn(Optional.of(new GeoLocation(LAT, LON, "Pune", "IN")));
        when(pincodeLocationRepository.save(any(PincodeLocation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(weatherRecordRepository.findByPincodeAndForDate(PINCODE, TODAY)).thenReturn(Optional.of(freshRecord()));

        WeatherLookupResult result = service.getWeather(PINCODE, TODAY);

        ArgumentCaptor<PincodeLocation> captor = ArgumentCaptor.forClass(PincodeLocation.class);
        verify(pincodeLocationRepository).save(captor.capture());
        PincodeLocation saved = captor.getValue();
        assertThat(saved.getPincode()).isEqualTo(PINCODE);
        assertThat(saved.getLatitude()).isEqualTo(LAT);
        assertThat(saved.getLongitude()).isEqualTo(LON);
        assertThat(saved.getPlaceName()).isEqualTo("Pune");
        assertThat(saved.getCountry()).isEqualTo("IN");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);

        assertThat(result.location()).isSameAs(saved);
        assertThat(result.locationSource()).isEqualTo(DataSource.OPENWEATHER);
    }

    @Test
    void concurrentLocationInsertIsToleratedByReReading() {
        PincodeLocation winner = storedLocation();
        when(pincodeLocationRepository.findById(PINCODE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(geocodingClient.lookup(PINCODE, "IN"))
                .thenReturn(Optional.of(new GeoLocation(LAT, LON, "Pune", "IN")));
        when(pincodeLocationRepository.save(any(PincodeLocation.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(weatherRecordRepository.findByPincodeAndForDate(PINCODE, TODAY)).thenReturn(Optional.of(freshRecord()));

        WeatherLookupResult result = service.getWeather(PINCODE, TODAY);

        assertThat(result.location()).isSameAs(winner);
        assertThat(result.weatherSource()).isEqualTo(DataSource.DATABASE);
        verify(pincodeLocationRepository).save(any(PincodeLocation.class));
    }

    // ---------------------------------------------------------------- weather: today

    @Test
    void todayWithNoRowCallsProviderAndPersistsFullyMappedRecord() {
        when(pincodeLocationRepository.findById(PINCODE)).thenReturn(Optional.of(storedLocation()));
        when(weatherRecordRepository.findByPincodeAndForDate(PINCODE, TODAY)).thenReturn(Optional.empty());
        WeatherObservation obs = observation();
        when(weatherClient.currentWeather(LAT, LON)).thenReturn(obs);
        when(weatherRecordRepository.save(any(WeatherRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        WeatherLookupResult result = service.getWeather(PINCODE, TODAY);

        ArgumentCaptor<WeatherRecord> captor = ArgumentCaptor.forClass(WeatherRecord.class);
        verify(weatherRecordRepository).save(captor.capture());
        WeatherRecord saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getPincode()).isEqualTo(PINCODE);
        assertThat(saved.getForDate()).isEqualTo(TODAY);
        assertThat(saved.getTemperature()).isEqualTo(obs.temperature());
        assertThat(saved.getFeelsLike()).isEqualTo(obs.feelsLike());
        assertThat(saved.getTempMin()).isEqualTo(obs.tempMin());
        assertThat(saved.getTempMax()).isEqualTo(obs.tempMax());
        assertThat(saved.getPressure()).isEqualTo(obs.pressure());
        assertThat(saved.getHumidity()).isEqualTo(obs.humidity());
        assertThat(saved.getWeatherMain()).isEqualTo(obs.weatherMain());
        assertThat(saved.getWeatherDescription()).isEqualTo(obs.weatherDescription());
        assertThat(saved.getWindSpeed()).isEqualTo(obs.windSpeed());
        assertThat(saved.getWindDegree()).isEqualTo(obs.windDegree());
        assertThat(saved.getCloudiness()).isEqualTo(obs.cloudiness());
        assertThat(saved.getVisibility()).isEqualTo(obs.visibility());
        assertThat(saved.getSunrise()).isEqualTo(obs.sunrise());
        assertThat(saved.getSunset()).isEqualTo(obs.sunset());
        assertThat(saved.getObservedAt()).isEqualTo(obs.observedAt());
        assertThat(saved.getFetchedAt()).isEqualTo(NOW);
        assertThat(saved.getSource()).isEqualTo(WeatherRecord.SOURCE_OPENWEATHER_CURRENT);

        assertThat(result.weather()).isSameAs(saved);
        assertThat(result.locationSource()).isEqualTo(DataSource.DATABASE);
        assertThat(result.weatherSource()).isEqualTo(DataSource.OPENWEATHER);
    }

    @Test
    void todayWithFreshRowIsServedFromDatabaseWithoutProviderCall() {
        WeatherRecord fresh = freshRecord();
        when(pincodeLocationRepository.findById(PINCODE)).thenReturn(Optional.of(storedLocation()));
        when(weatherRecordRepository.findByPincodeAndForDate(PINCODE, TODAY)).thenReturn(Optional.of(fresh));

        WeatherLookupResult result = service.getWeather(PINCODE, TODAY);

        assertThat(result.weather()).isSameAs(fresh);
        assertThat(result.weatherSource()).isEqualTo(DataSource.DATABASE);
        verifyNoInteractions(weatherClient);
        verify(weatherRecordRepository, never()).save(any());
    }

    @Test
    void todayWithStaleRowRefreshesInPlaceKeepingId() {
        WeatherRecord stale = record(7L, TODAY, NOW.minus(TTL).minusSeconds(1), 20.0);
        when(pincodeLocationRepository.findById(PINCODE)).thenReturn(Optional.of(storedLocation()));
        when(weatherRecordRepository.findByPincodeAndForDate(PINCODE, TODAY)).thenReturn(Optional.of(stale));
        WeatherObservation obs = observation();
        when(weatherClient.currentWeather(LAT, LON)).thenReturn(obs);
        when(weatherRecordRepository.save(any(WeatherRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        WeatherLookupResult result = service.getWeather(PINCODE, TODAY);

        ArgumentCaptor<WeatherRecord> captor = ArgumentCaptor.forClass(WeatherRecord.class);
        verify(weatherRecordRepository).save(captor.capture());
        WeatherRecord saved = captor.getValue();
        assertThat(saved).isSameAs(stale);
        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getTemperature()).isEqualTo(obs.temperature());
        assertThat(saved.getObservedAt()).isEqualTo(obs.observedAt());
        assertThat(saved.getFetchedAt()).isEqualTo(NOW);

        assertThat(result.weather()).isSameAs(stale);
        assertThat(result.weatherSource()).isEqualTo(DataSource.OPENWEATHER);
    }

    @Test
    void rowExactlyAtTtlBoundaryIsConsideredStale() {
        WeatherRecord boundary = record(3L, TODAY, NOW.minus(TTL), 20.0);
        when(pincodeLocationRepository.findById(PINCODE)).thenReturn(Optional.of(storedLocation()));
        when(weatherRecordRepository.findByPincodeAndForDate(PINCODE, TODAY)).thenReturn(Optional.of(boundary));
        when(weatherClient.currentWeather(LAT, LON)).thenReturn(observation());
        when(weatherRecordRepository.save(any(WeatherRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        WeatherLookupResult result = service.getWeather(PINCODE, TODAY);

        assertThat(result.weatherSource()).isEqualTo(DataSource.OPENWEATHER);
        verify(weatherClient).currentWeather(LAT, LON);
    }

    @Test
    void concurrentWeatherInsertIsToleratedByReReading() {
        WeatherRecord winner = record(9L, TODAY, NOW, 31.0);
        when(pincodeLocationRepository.findById(PINCODE)).thenReturn(Optional.of(storedLocation()));
        when(weatherRecordRepository.findByPincodeAndForDate(PINCODE, TODAY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(weatherClient.currentWeather(LAT, LON)).thenReturn(observation());
        when(weatherRecordRepository.save(any(WeatherRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        WeatherLookupResult result = service.getWeather(PINCODE, TODAY);

        assertThat(result.weather()).isSameAs(winner);
        assertThat(result.weatherSource()).isEqualTo(DataSource.OPENWEATHER);
    }

    // ---------------------------------------------------------------- weather: past

    @Test
    void pastDateWithRowIsServedFromDatabaseRegardlessOfAge() {
        WeatherRecord old = record(5L, YESTERDAY, NOW.minus(Duration.ofDays(1)), 25.5);
        when(pincodeLocationRepository.findById(PINCODE)).thenReturn(Optional.of(storedLocation()));
        when(weatherRecordRepository.findByPincodeAndForDate(PINCODE, YESTERDAY)).thenReturn(Optional.of(old));

        WeatherLookupResult result = service.getWeather(PINCODE, YESTERDAY);

        assertThat(result.weather()).isSameAs(old);
        assertThat(result.weatherSource()).isEqualTo(DataSource.DATABASE);
        verifyNoInteractions(weatherClient);
        verify(weatherRecordRepository, never()).save(any());
    }

    @Test
    void pastDateWithNoRowThrowsUnavailableAndNeverCallsProvider() {
        when(pincodeLocationRepository.findById(PINCODE)).thenReturn(Optional.of(storedLocation()));
        when(weatherRecordRepository.findByPincodeAndForDate(PINCODE, YESTERDAY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWeather(PINCODE, YESTERDAY))
                .isInstanceOf(WeatherDataUnavailableException.class)
                .hasMessageContaining(PINCODE)
                .hasMessageContaining(YESTERDAY.toString())
                .hasMessageContaining("no history");

        verify(weatherClient, never()).currentWeather(anyDouble(), anyDouble());
        verify(weatherRecordRepository, never()).save(any());
        verify(geocodingClient, never()).lookup(anyString(), anyString());
    }

    // ---------------------------------------------------------------- fixtures

    private static PincodeLocation storedLocation() {
        return new PincodeLocation(PINCODE, LAT, LON, "Pune", "IN", NOW.minus(Duration.ofDays(30)));
    }

    /** A same-day row fetched well within the TTL. */
    private static WeatherRecord freshRecord() {
        return record(1L, TODAY, NOW.minus(Duration.ofMinutes(5)), 28.0);
    }

    private static WeatherRecord record(Long id, LocalDate forDate, Instant fetchedAt, double temperature) {
        return WeatherRecord.builder()
                .id(id)
                .pincode(PINCODE)
                .forDate(forDate)
                .temperature(temperature)
                .observedAt(fetchedAt.minusSeconds(60))
                .fetchedAt(fetchedAt)
                .source(WeatherRecord.SOURCE_OPENWEATHER_CURRENT)
                .build();
    }

    private static WeatherObservation observation() {
        return new WeatherObservation(
                NOW.minusSeconds(120),
                30.2, 33.1, 29.0, 31.5,
                1008, 71,
                "Clouds", "broken clouds",
                3.6, 240, 75, 8000,
                ZonedDateTime.of(2026, 9, 5, 6, 20, 0, 0, ZONE).toInstant(),
                ZonedDateTime.of(2026, 9, 5, 18, 45, 0, 0, ZONE).toInstant());
    }
}
