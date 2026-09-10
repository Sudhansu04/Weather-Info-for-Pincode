package com.pincodeweather.service;

/** Where the returned weather came from. Lets callers verify the caching behaviour. */
public enum DataSource {
    /** Served from the RDBMS without calling any external API. */
    DATABASE,
    /** Fetched live from the provider on this request and persisted. */
    OPENWEATHER
}
