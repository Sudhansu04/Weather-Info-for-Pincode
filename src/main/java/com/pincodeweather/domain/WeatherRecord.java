package com.pincodeweather.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Weather snapshot for a pincode on a given calendar date. Unique per (pincode, forDate).
 * Temperatures are in the configured unit system (Celsius by default), pressure in hPa,
 * humidity/cloudiness in %, wind speed in m/s, visibility in metres.
 */
@Entity
@Table(name = "weather_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_weather_record_pincode_date", columnNames = {"pincode", "for_date"}))
public class WeatherRecord {

    public static final String SOURCE_OPENWEATHER_CURRENT = "OPENWEATHER_CURRENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "pincode", nullable = false, length = 10)
    private String pincode;

    @Column(name = "for_date", nullable = false)
    private LocalDate forDate;

    @Column(name = "temperature") private Double temperature;
    @Column(name = "feels_like") private Double feelsLike;
    @Column(name = "temp_min") private Double tempMin;
    @Column(name = "temp_max") private Double tempMax;
    @Column(name = "pressure") private Integer pressure;
    @Column(name = "humidity") private Integer humidity;
    @Column(name = "weather_main", length = 64) private String weatherMain;
    @Column(name = "weather_description") private String weatherDescription;
    @Column(name = "wind_speed") private Double windSpeed;
    @Column(name = "wind_degree") private Integer windDegree;
    @Column(name = "cloudiness") private Integer cloudiness;
    @Column(name = "visibility") private Integer visibility;
    @Column(name = "sunrise") private Instant sunrise;
    @Column(name = "sunset") private Instant sunset;

    /** Timestamp of the observation as reported by the provider ("dt"). */
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    /** When this row was fetched from the provider. Drives same-day refresh. */
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    protected WeatherRecord() {
        // JPA
    }

    private WeatherRecord(Builder b) {
        this.id = b.id;
        this.pincode = b.pincode;
        this.forDate = b.forDate;
        this.temperature = b.temperature;
        this.feelsLike = b.feelsLike;
        this.tempMin = b.tempMin;
        this.tempMax = b.tempMax;
        this.pressure = b.pressure;
        this.humidity = b.humidity;
        this.weatherMain = b.weatherMain;
        this.weatherDescription = b.weatherDescription;
        this.windSpeed = b.windSpeed;
        this.windDegree = b.windDegree;
        this.cloudiness = b.cloudiness;
        this.visibility = b.visibility;
        this.sunrise = b.sunrise;
        this.sunset = b.sunset;
        this.observedAt = b.observedAt;
        this.fetchedAt = b.fetchedAt;
        this.source = b.source;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Overwrites the weather values of this row with a fresh observation, keeping id/pincode/forDate.
     * Used for same-day refreshes so the (pincode, forDate) row stays unique.
     */
    public void refreshFrom(WeatherRecord fresh) {
        this.temperature = fresh.temperature;
        this.feelsLike = fresh.feelsLike;
        this.tempMin = fresh.tempMin;
        this.tempMax = fresh.tempMax;
        this.pressure = fresh.pressure;
        this.humidity = fresh.humidity;
        this.weatherMain = fresh.weatherMain;
        this.weatherDescription = fresh.weatherDescription;
        this.windSpeed = fresh.windSpeed;
        this.windDegree = fresh.windDegree;
        this.cloudiness = fresh.cloudiness;
        this.visibility = fresh.visibility;
        this.sunrise = fresh.sunrise;
        this.sunset = fresh.sunset;
        this.observedAt = fresh.observedAt;
        this.fetchedAt = fresh.fetchedAt;
        this.source = fresh.source;
    }

    public Long getId() { return id; }
    public String getPincode() { return pincode; }
    public LocalDate getForDate() { return forDate; }
    public Double getTemperature() { return temperature; }
    public Double getFeelsLike() { return feelsLike; }
    public Double getTempMin() { return tempMin; }
    public Double getTempMax() { return tempMax; }
    public Integer getPressure() { return pressure; }
    public Integer getHumidity() { return humidity; }
    public String getWeatherMain() { return weatherMain; }
    public String getWeatherDescription() { return weatherDescription; }
    public Double getWindSpeed() { return windSpeed; }
    public Integer getWindDegree() { return windDegree; }
    public Integer getCloudiness() { return cloudiness; }
    public Integer getVisibility() { return visibility; }
    public Instant getSunrise() { return sunrise; }
    public Instant getSunset() { return sunset; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getFetchedAt() { return fetchedAt; }
    public String getSource() { return source; }

    public static final class Builder {
        private Long id;
        private String pincode;
        private LocalDate forDate;
        private Double temperature, feelsLike, tempMin, tempMax;
        private Integer pressure, humidity;
        private String weatherMain, weatherDescription;
        private Double windSpeed;
        private Integer windDegree, cloudiness, visibility;
        private Instant sunrise, sunset, observedAt, fetchedAt;
        private String source = SOURCE_OPENWEATHER_CURRENT;

        private Builder() {}

        public Builder id(Long v) { this.id = v; return this; }
        public Builder pincode(String v) { this.pincode = v; return this; }
        public Builder forDate(LocalDate v) { this.forDate = v; return this; }
        public Builder temperature(Double v) { this.temperature = v; return this; }
        public Builder feelsLike(Double v) { this.feelsLike = v; return this; }
        public Builder tempMin(Double v) { this.tempMin = v; return this; }
        public Builder tempMax(Double v) { this.tempMax = v; return this; }
        public Builder pressure(Integer v) { this.pressure = v; return this; }
        public Builder humidity(Integer v) { this.humidity = v; return this; }
        public Builder weatherMain(String v) { this.weatherMain = v; return this; }
        public Builder weatherDescription(String v) { this.weatherDescription = v; return this; }
        public Builder windSpeed(Double v) { this.windSpeed = v; return this; }
        public Builder windDegree(Integer v) { this.windDegree = v; return this; }
        public Builder cloudiness(Integer v) { this.cloudiness = v; return this; }
        public Builder visibility(Integer v) { this.visibility = v; return this; }
        public Builder sunrise(Instant v) { this.sunrise = v; return this; }
        public Builder sunset(Instant v) { this.sunset = v; return this; }
        public Builder observedAt(Instant v) { this.observedAt = v; return this; }
        public Builder fetchedAt(Instant v) { this.fetchedAt = v; return this; }
        public Builder source(String v) { this.source = v; return this; }

        public WeatherRecord build() {
            if (pincode == null || forDate == null || observedAt == null || fetchedAt == null || source == null) {
                throw new IllegalStateException("pincode, forDate, observedAt, fetchedAt and source are required");
            }
            return new WeatherRecord(this);
        }
    }
}
