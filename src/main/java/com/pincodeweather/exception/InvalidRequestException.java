package com.pincodeweather.exception;

/** Semantically invalid input (e.g. for_date in the future). Maps to HTTP 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
