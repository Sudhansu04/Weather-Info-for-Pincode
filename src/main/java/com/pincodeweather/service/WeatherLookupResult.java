package com.pincodeweather.service;

import com.pincodeweather.domain.PincodeLocation;
import com.pincodeweather.domain.WeatherRecord;

/**
 * Outcome of a weather lookup: the pincode's location, the weather row, and whether the weather was cached.
 *
 * @param locationSource whether the lat/long came from DB or a fresh geocoding call
 * @param weatherSource  whether the weather came from DB or a fresh provider call
 */
public record WeatherLookupResult(
        PincodeLocation location,
        WeatherRecord weather,
        DataSource locationSource,
        DataSource weatherSource
) {
}
