package com.pincodeweather.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the JPA mapping against the Flyway-managed schema (Hibernate runs with {@code ddl-auto=validate})
 * and that the database constraints behave as the service relies on them.
 */
@DataJpaTest
class WeatherRecordRepositoryTest {

    private static final String PINCODE = "411014";
    private static final LocalDate DATE = LocalDate.of(2026, 9, 5);
    private static final Instant NOW = Instant.parse("2026-09-05T04:30:00Z");

    @Autowired private PincodeLocationRepository pincodeLocationRepository;
    @Autowired private WeatherRecordRepository weatherRecordRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void savesLocationAndWeatherAndFindsByPincodeAndDate() {
        pincodeLocationRepository.saveAndFlush(location(PINCODE));
        WeatherRecord saved = weatherRecordRepository.saveAndFlush(record(PINCODE, DATE, 30.2));
        entityManager.clear();

        Optional<WeatherRecord> found = weatherRecordRepository.findByPincodeAndForDate(PINCODE, DATE);

        assertThat(found).isPresent();
        WeatherRecord row = found.get();
        assertThat(row.getId()).isEqualTo(saved.getId());
        assertThat(row.getPincode()).isEqualTo(PINCODE);
        assertThat(row.getForDate()).isEqualTo(DATE);
        assertThat(row.getTemperature()).isEqualTo(30.2);
        assertThat(row.getFeelsLike()).isEqualTo(33.1);
        assertThat(row.getPressure()).isEqualTo(1008);
        assertThat(row.getHumidity()).isEqualTo(71);
        assertThat(row.getWeatherMain()).isEqualTo("Clouds");
        assertThat(row.getWeatherDescription()).isEqualTo("broken clouds");
        assertThat(row.getWindSpeed()).isEqualTo(3.6);
        assertThat(row.getWindDegree()).isEqualTo(240);
        assertThat(row.getCloudiness()).isEqualTo(75);
        assertThat(row.getVisibility()).isEqualTo(8000);
        assertThat(row.getSunrise()).isEqualTo(Instant.parse("2026-09-05T00:50:00Z"));
        assertThat(row.getSunset()).isEqualTo(Instant.parse("2026-09-05T13:15:00Z"));
        assertThat(row.getObservedAt()).isEqualTo(NOW.minusSeconds(120));
        assertThat(row.getFetchedAt()).isEqualTo(NOW);
        assertThat(row.getSource()).isEqualTo(WeatherRecord.SOURCE_OPENWEATHER_CURRENT);

        assertThat(weatherRecordRepository.findByPincodeAndForDate(PINCODE, DATE.minusDays(1))).isEmpty();
        assertThat(weatherRecordRepository.findByPincodeAndForDate("999999", DATE)).isEmpty();
    }

    @Test
    void uniqueConstraintRejectsSecondRowForSamePincodeAndDate() {
        pincodeLocationRepository.saveAndFlush(location(PINCODE));
        weatherRecordRepository.saveAndFlush(record(PINCODE, DATE, 30.2));

        assertThatThrownBy(() -> weatherRecordRepository.saveAndFlush(record(PINCODE, DATE, 31.0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeyRejectsWeatherForUnknownPincode() {
        assertThatThrownBy(() -> weatherRecordRepository.saveAndFlush(record("000000", DATE, 30.2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static PincodeLocation location(String pincode) {
        return new PincodeLocation(pincode, 18.5679, 73.9143, "Pune", "IN", NOW);
    }

    private static WeatherRecord record(String pincode, LocalDate forDate, double temperature) {
        return WeatherRecord.builder()
                .pincode(pincode)
                .forDate(forDate)
                .temperature(temperature)
                .feelsLike(33.1)
                .tempMin(29.0)
                .tempMax(31.5)
                .pressure(1008)
                .humidity(71)
                .weatherMain("Clouds")
                .weatherDescription("broken clouds")
                .windSpeed(3.6)
                .windDegree(240)
                .cloudiness(75)
                .visibility(8000)
                .sunrise(Instant.parse("2026-09-05T00:50:00Z"))
                .sunset(Instant.parse("2026-09-05T13:15:00Z"))
                .observedAt(NOW.minusSeconds(120))
                .fetchedAt(NOW)
                .source(WeatherRecord.SOURCE_OPENWEATHER_CURRENT)
                .build();
    }
}
