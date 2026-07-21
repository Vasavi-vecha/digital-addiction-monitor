package com.digitaladdictionmonitor.backend.exception;

/** No usage logs exist yet for the requested userId. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("No usage logs found for userId=" + userId);
    }
}
