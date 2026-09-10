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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Default {@link WeatherService}: the RDBMS is a read-through cache in front of the geocoding and
 * current-weather providers. See the interface Javadoc for the exact algorithm.
 *
 * <p>Deliberately <em>not</em> wrapped in a single transaction: each repository call commits on its own, so
 * <ul>
 *   <li>a geocoded location stays cached even if the subsequent weather step fails (e.g. past date with no data,
 *       provider outage), which is the whole point of saving lat/long separately;</li>
 *   <li>no database connection is held open while waiting on external HTTP calls;</li>
 *   <li>the concurrent-insert handling below can re-read after a unique-constraint violation (inside one
 *       transaction that violation would have marked it rollback-only).</li>
 * </ul>
 */
@Service
public class WeatherServiceImpl implements WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherServiceImpl.class);

    private final PincodeLocationRepository pincodeLocationRepository;
    private final WeatherRecordRepository weatherRecordRepository;
    private final GeocodingClient geocodingClient;
    private final WeatherClient weatherClient;
    private final WeatherProperties properties;
    private final Clock clock;

    public WeatherServiceImpl(PincodeLocationRepository pincodeLocationRepository,
                              WeatherRecordRepository weatherRecordRepository,
                              GeocodingClient geocodingClient,
                              WeatherClient weatherClient,
                              WeatherProperties properties,
                              Clock clock) {
        this.pincodeLocationRepository = pincodeLocationRepository;
        this.weatherRecordRepository = weatherRecordRepository;
        this.geocodingClient = geocodingClient;
        this.weatherClient = weatherClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public WeatherLookupResult getWeather(String pincode, LocalDate forDate) {
        LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
        if (forDate.isAfter(today)) {
            throw new InvalidRequestException(
                    "for_date cannot be in the future (today is " + today + " in " + properties.zone() + ")");
        }

        LocationLookup location = resolveLocation(pincode);
        WeatherLookup weather = resolveWeather(location.location(), forDate, today);

        return new WeatherLookupResult(location.location(), weather.record(), location.source(), weather.source());
    }

    /** Step 2: location from the DB, else geocode once and persist. */
    private LocationLookup resolveLocation(String pincode) {
        Optional<PincodeLocation> cached = pincodeLocationRepository.findById(pincode);
        if (cached.isPresent()) {
            log.debug("Location cache hit for pincode {}", pincode);
            return new LocationLookup(cached.get(), DataSource.DATABASE);
        }

        log.info("Location cache miss for pincode {}; calling geocoding provider", pincode);
        GeoLocation geo = geocodingClient.lookup(pincode, properties.defaultCountry())
                .orElseThrow(() -> new PincodeNotFoundException(pincode));

        PincodeLocation fresh = new PincodeLocation(
                pincode, geo.latitude(), geo.longitude(), geo.placeName(), geo.country(), clock.instant());
        try {
            return new LocationLookup(pincodeLocationRepository.save(fresh), DataSource.OPENWEATHER);
        } catch (DataIntegrityViolationException race) {
            // Another request inserted the same pincode between our read and write; the stored row wins.
            log.debug("Concurrent insert detected for pincode {}; re-reading location", pincode);
            PincodeLocation existing = pincodeLocationRepository.findById(pincode).orElseThrow(() -> race);
            return new LocationLookup(existing, DataSource.OPENWEATHER);
        }
    }

    /** Steps 3-5: weather from the DB when fresh enough, else from the provider (today only). */
    private WeatherLookup resolveWeather(PincodeLocation location, LocalDate forDate, LocalDate today) {
        String pincode = location.getPincode();
        Instant now = clock.instant();
        Optional<WeatherRecord> stored = weatherRecordRepository.findByPincodeAndForDate(pincode, forDate);

        if (stored.isPresent() && isServable(stored.get(), forDate, today, now)) {
            log.debug("Weather cache hit for pincode {} on {}", pincode, forDate);
            return new WeatherLookup(stored.get(), DataSource.DATABASE);
        }

        if (forDate.isEqual(today)) {
            log.info("Weather cache {} for pincode {} on {}; calling current-weather provider",
                    stored.isPresent() ? "stale" : "miss", pincode, forDate);
            WeatherObservation observation = weatherClient.currentWeather(location.getLatitude(), location.getLongitude());
            WeatherRecord fresh = toRecord(observation, pincode, forDate, now);
            return new WeatherLookup(upsert(stored.orElse(null), fresh), DataSource.OPENWEATHER);
        }

        throw new WeatherDataUnavailableException(
                "No stored weather for pincode " + pincode + " on " + forDate
                        + ". Historical weather is only available if it was fetched on that day"
                        + " (the OpenWeather current-weather API has no history).");
    }

    /** A stored row is servable if its date is in the past (immutable) or it is younger than the same-day TTL. */
    private boolean isServable(WeatherRecord record, LocalDate forDate, LocalDate today, Instant now) {
        return forDate.isBefore(today)
                || record.getFetchedAt().plus(properties.sameDayRefreshAfter()).isAfter(now);
    }

    /** Refreshes an existing row in place, or inserts the fresh one (tolerating a concurrent insert). */
    private WeatherRecord upsert(WeatherRecord existing, WeatherRecord fresh) {
        if (existing != null) {
            existing.refreshFrom(fresh);
            return weatherRecordRepository.save(existing);
        }
        try {
            return weatherRecordRepository.save(fresh);
        } catch (DataIntegrityViolationException race) {
            log.debug("Concurrent insert detected for weather {} on {}; re-reading record",
                    fresh.getPincode(), fresh.getForDate());
            return weatherRecordRepository.findByPincodeAndForDate(fresh.getPincode(), fresh.getForDate())
                    .orElseThrow(() -> race);
        }
    }

    /** Maps a provider observation to a new (unsaved) weather row. */
    private WeatherRecord toRecord(WeatherObservation obs, String pincode, LocalDate forDate, Instant now) {
        return WeatherRecord.builder()
                .pincode(pincode)
                .forDate(forDate)
                .temperature(obs.temperature())
                .feelsLike(obs.feelsLike())
                .tempMin(obs.tempMin())
                .tempMax(obs.tempMax())
                .pressure(obs.pressure())
                .humidity(obs.humidity())
                .weatherMain(obs.weatherMain())
                .weatherDescription(obs.weatherDescription())
                .windSpeed(obs.windSpeed())
                .windDegree(obs.windDegree())
                .cloudiness(obs.cloudiness())
                .visibility(obs.visibility())
                .sunrise(obs.sunrise())
                .sunset(obs.sunset())
                .observedAt(obs.observedAt())
                .fetchedAt(now)
                .source(WeatherRecord.SOURCE_OPENWEATHER_CURRENT)
                .build();
    }

    private record LocationLookup(PincodeLocation location, DataSource source) {}

    private record WeatherLookup(WeatherRecord record, DataSource source) {}
}
