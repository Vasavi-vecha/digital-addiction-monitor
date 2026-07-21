package com.digitaladdictionmonitor.backend.exception;

/** ml-service could not be reached, or returned a non-2xx/unparseable response. */
public class MlServiceUnavailableException extends RuntimeException {

    public MlServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
