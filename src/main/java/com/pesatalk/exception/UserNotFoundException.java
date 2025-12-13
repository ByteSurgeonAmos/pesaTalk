package com.pesatalk.exception;

public class UserNotFoundException extends PesaTalkException {

    public UserNotFoundException(String identifier) {
        super("USER_NOT_FOUND", "User not found: " + identifier);
    }
}
