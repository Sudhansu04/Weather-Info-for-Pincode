package com.pincodeweather.api.dto;

import com.pincodeweather.domain.PincodeLocation;
import com.pincodeweather.domain.WeatherRecord;
import com.pincodeweather.service.DataSource;
import com.pincodeweather.service.WeatherLookupResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Public representation of a weather lookup for a pincode on a date.
 *
 * <p>Built from a {@link WeatherLookupResult} via {@link #from(WeatherLookupResult)}; the response also reports
 * whether the location and the weather were served from the database cache or fetched live, so clients can
 * verify the caching behaviour.
 */
@Schema(description = "Weather for an Indian pincode on a given date.")
public record WeatherResponse(
        @Schema(description = "6-digit Indian PIN code the weather belongs to.", example = "751024")
        String pincode,
        @Schema(description = "Calendar date (in the configured zone) the weather is for.", example = "2020-10-15")
        LocalDate forDate,
        @Schema(description = "Resolved geographic location of the pincode.")
        Location location,
        @Schema(description = "Weather observation values in metric units.")
        Weather weather,
        @Schema(description = "Units of measure used by the weather values.")
        Units units,
        @Schema(description = "DATABASE when served from cache; OPENWEATHER when fetched live on this request.", example = "OPENWEATHER")
        DataSource weatherSource,
        @Schema(description = "When the returned weather row was fetched from the provider.", example = "2020-10-15T04:05:06Z")
        Instant fetchedAt
) {

    /** Geographic coordinates of the pincode and where they were obtained from. */
    @Schema(description = "Geographic location of the pincode.")
    public record Location(
            @Schema(description = "Latitude in decimal degrees.", example = "18.5679") double latitude,
            @Schema(description = "Longitude in decimal degrees.", example = "73.9143") double longitude,
            @Schema(description = "Locality or city name reported by the geocoder.", example = "Pune", nullable = true) String placeName,
            @Schema(description = "ISO 3166 alpha-2 country code.", example = "IN") String country,
            @Schema(description = "DATABASE when the coordinates were cached; OPENWEATHER when geocoded on this request.", example = "DATABASE")
            DataSource source
    ) {
    }

    /** Weather observation values. All fields except {@code observedAt} may be null if the provider omitted them. */
    @Schema(description = "Weather observation values (metric).")
    public record Weather(
            @Schema(description = "Air temperature.", example = "27.3", nullable = true) Double temperature,
            @Schema(description = "Perceived temperature.", example = "29.1", nullable = true) Double feelsLike,
            @Schema(description = "Minimum observed temperature.", example = "26.0", nullable = true) Double tempMin,
            @Schema(description = "Maximum observed temperature.", example = "28.5", nullable = true) Double tempMax,
            @Schema(description = "Atmospheric pressure.", example = "1010", nullable = true) Integer pressure,
            @Schema(description = "Relative humidity in percent.", example = "65", nullable = true) Integer humidity,
            @Schema(description = "Weather group, e.g. Clear, Clouds, Rain.", example = "Clouds", nullable = true) String condition,
            @Schema(description = "Human-readable weather description.", example = "scattered clouds", nullable = true) String description,
            @Schema(description = "Wind speed.", example = "3.6", nullable = true) Double windSpeed,
            @Schema(description = "Wind direction in meteorological degrees.", example = "250", nullable = true) Integer windDegree,
            @Schema(description = "Cloud cover in percent.", example = "40", nullable = true) Integer cloudiness,
            @Schema(description = "Visibility.", example = "10000", nullable = true) Integer visibility,
            @Schema(description = "Sunrise time (UTC instant).", example = "2020-10-15T01:00:12Z", nullable = true) Instant sunrise,
            @Schema(description = "Sunset time (UTC instant).", example = "2020-10-15T12:35:44Z", nullable = true) Instant sunset,
            @Schema(description = "When the observation was taken, as reported by the provider.", example = "2020-10-15T04:00:00Z") Instant observedAt
    ) {
    }

    /** Units of measure for the weather values. */
    @Schema(description = "Units of measure for the weather values.")
    public record Units(
            @Schema(example = "°C") String temperature,
            @Schema(example = "m/s") String windSpeed,
            @Schema(example = "hPa") String pressure,
            @Schema(example = "m") String visibility
    ) {
        /** Units used by OpenWeather's {@code metric} unit system, which this service is configured with. */
        public static final Units METRIC = new Units("°C", "m/s", "hPa", "m");
    }

    /**
     * Maps a service-layer lookup result to the API representation.
     */
    public static WeatherResponse from(WeatherLookupResult result) {
        PincodeLocation loc = result.location();
        WeatherRecord w = result.weather();

        Location location = new Location(
                loc.getLatitude(),
                loc.getLongitude(),
                loc.getPlaceName(),
                loc.getCountry(),
                result.locationSource());

        Weather weather = new Weather(
                w.getTemperature(),
                w.getFeelsLike(),
                w.getTempMin(),
                w.getTempMax(),
                w.getPressure(),
                w.getHumidity(),
                w.getWeatherMain(),
                w.getWeatherDescription(),
                w.getWindSpeed(),
                w.getWindDegree(),
                w.getCloudiness(),
                w.getVisibility(),
                w.getSunrise(),
                w.getSunset(),
                w.getObservedAt());

        return new WeatherResponse(
                w.getPincode(),
                w.getForDate(),
                location,
                weather,
                Units.METRIC,
                result.weatherSource(),
                w.getFetchedAt());
    }
}
