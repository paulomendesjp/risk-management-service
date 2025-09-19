package com.interview.challenge.kraken.exception;

/**
 * Custom exception for Kraken API errors
 */
public class KrakenApiException extends RuntimeException {

    private String errorCode;
    private int httpStatus;

    public KrakenApiException(String message) {
        super(message);
    }

    public KrakenApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public KrakenApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public KrakenApiException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}