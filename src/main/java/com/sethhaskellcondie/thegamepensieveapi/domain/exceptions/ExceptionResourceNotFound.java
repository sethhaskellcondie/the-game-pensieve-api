package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

public class ExceptionResourceNotFound extends RuntimeException {
    public ExceptionResourceNotFound(String message) {
        super("Resource Not Found: " + message);
    }

    public ExceptionResourceNotFound(String resourceName, int id) {
        super("Resource Not Found: " + resourceName +
                " not found in the database with id: " + id);
    }

    public ExceptionResourceNotFound(String message, String resourceName, int id) {
        super("Resource Not Found: " + message + " " +
                resourceName + " not found with id: " + id);
    }
}
