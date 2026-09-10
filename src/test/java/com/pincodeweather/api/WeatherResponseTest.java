package com.pincodeweather.api;

import com.pincodeweather.api.dto.WeatherResponse;
import com.pincodeweather.domain.PincodeLocation;
import com.pincodeweather.domain.WeatherRecord;
import com.pincodeweather.service.DataSource;
import com.pincodeweather.service.WeatherLookupResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherResponseTest {

    private static final Instant OBSERVED = Instant.parse("2020-10-15T04:00:00Z");
    private static final Instant FETCHED = Instant.parse("2020-10-15T04:05:06Z");

    @Test
    void mapsEveryFieldFromLookupResult() {
        PincodeLocation location = new PincodeLocation("411014", 18.5679, 73.9143, "Pune", "IN",
                Instant.parse("2020-10-01T00:00:00Z"));
        WeatherRecord record = WeatherRecord.builder()
                .pincode("411014")
                .forDate(LocalDate.of(2020, 10, 15))
                .temperature(27.3).feelsLike(29.1).tempMin(26.0).tempMax(28.5)
                .pressure(1010).humidity(65)
                .weatherMain("Clouds").weatherDescription("scattered clouds")
                .windSpeed(3.6).windDegree(250).cloudiness(40).visibility(10000)
                .sunrise(Instant.parse("2020-10-15T01:00:12Z"))
                .sunset(Instant.parse("2020-10-15T12:35:44Z"))
                .observedAt(OBSERVED)
                .fetchedAt(FETCHED)
                .build();

        WeatherResponse response = WeatherResponse.from(
                new WeatherLookupResult(location, record, DataSource.DATABASE, DataSource.OPENWEATHER));

        assertThat(response.pincode()).isEqualTo("411014");
        assertThat(response.forDate()).isEqualTo(LocalDate.of(2020, 10, 15));
        assertThat(response.weatherSource()).isEqualTo(DataSource.OPENWEATHER);
        assertThat(response.fetchedAt()).isEqualTo(FETCHED);
        assertThat(response.units()).isSameAs(WeatherResponse.Units.METRIC);
        assertThat(response.units()).isEqualTo(new WeatherResponse.Units("°C", "m/s", "hPa", "m"));

        assertThat(response.location()).isEqualTo(
                new WeatherResponse.Location(18.5679, 73.9143, "Pune", "IN", DataSource.DATABASE));

        assertThat(response.weather()).isEqualTo(new WeatherResponse.Weather(
                27.3, 29.1, 26.0, 28.5, 1010, 65, "Clouds", "scattered clouds",
                3.6, 250, 40, 10000,
                Instant.parse("2020-10-15T01:00:12Z"), Instant.parse("2020-10-15T12:35:44Z"), OBSERVED));
    }

    @Test
    void tolerantOfOptionalWeatherFieldsBeingNull() {
        PincodeLocation location = new PincodeLocation("110001", 28.6304, 77.2177, null, "IN", Instant.EPOCH);
        WeatherRecord record = WeatherRecord.builder()
                .pincode("110001")
                .forDate(LocalDate.of(2021, 1, 1))
                .observedAt(OBSERVED)
                .fetchedAt(FETCHED)
                .build();

        WeatherResponse response = WeatherResponse.from(
                new WeatherLookupResult(location, record, DataSource.OPENWEATHER, DataSource.DATABASE));

        assertThat(response.location().placeName()).isNull();
        assertThat(response.location().source()).isEqualTo(DataSource.OPENWEATHER);
        assertThat(response.weatherSource()).isEqualTo(DataSource.DATABASE);
        assertThat(response.weather().temperature()).isNull();
        assertThat(response.weather().condition()).isNull();
        assertThat(response.weather().sunrise()).isNull();
        assertThat(response.weather().observedAt()).isEqualTo(OBSERVED);
    }
}
