package com.pincodeweather.exception;

/** The geocoding provider does not know the requested pincode. Maps to HTTP 404. */
public class PincodeNotFoundException extends RuntimeException {
    private final String pincode;

    public PincodeNotFoundException(String pincode) {
        super("No location found for pincode " + pincode);
        this.pincode = pincode;
    }

    public String getPincode() { return pincode; }
}
