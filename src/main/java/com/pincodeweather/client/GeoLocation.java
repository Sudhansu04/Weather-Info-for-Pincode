package com.pincodeweather.client;

/**
 * Provider-agnostic geocoding result for a pincode.
 */
public record GeoLocation(double latitude, double longitude, String placeName, String country) {
}
