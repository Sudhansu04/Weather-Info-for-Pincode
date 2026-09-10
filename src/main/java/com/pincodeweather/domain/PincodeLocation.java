package com.pincodeweather.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Geographic coordinates of a pincode. Stored separately from weather so geocoding happens once per pincode.
 */
@Entity
@Table(name = "pincode_location")
public class PincodeLocation {

    @Id
    @Column(name = "pincode", nullable = false, length = 10)
    private String pincode;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "place_name")
    private String placeName;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PincodeLocation() {
        // JPA
    }

    public PincodeLocation(String pincode, double latitude, double longitude, String placeName, String country, Instant createdAt) {
        this.pincode = pincode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeName = placeName;
        this.country = country;
        this.createdAt = createdAt;
    }

    public String getPincode() { return pincode; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getPlaceName() { return placeName; }
    public String getCountry() { return country; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PincodeLocation that)) return false;
        return Objects.equals(pincode, that.pincode);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(pincode);
    }
}
