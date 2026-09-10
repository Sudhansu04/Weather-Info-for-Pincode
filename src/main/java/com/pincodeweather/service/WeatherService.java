package com.pincodeweather.service;

import java.time.LocalDate;

/**
 * Returns weather for a pincode on a date, using the RDBMS as a cache in front of the geocoding and weather providers.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Reject {@code forDate} in the future (per configured zone) with {@link com.pincodeweather.exception.InvalidRequestException}.</li>
 *   <li>Load lat/long for the pincode from {@code pincode_location}; on a miss, geocode once and persist.
 *       Unknown pincode → {@link com.pincodeweather.exception.PincodeNotFoundException}.</li>
 *   <li>Load {@code weather_record} for (pincode, forDate). Return it if present and either the date is in the past
 *       or the row is younger than {@code weather.same-day-refresh-after}.</li>
 *   <li>If {@code forDate} is today, call the current-weather provider, insert or refresh the row, and return it.</li>
 *   <li>If {@code forDate} is in the past and nothing is stored, throw
 *       {@link com.pincodeweather.exception.WeatherDataUnavailableException} (the free current-weather API has no history).</li>
 * </ol>
 */
public interface WeatherService {

    WeatherLookupResult getWeather(String pincode, LocalDate forDate);
}
