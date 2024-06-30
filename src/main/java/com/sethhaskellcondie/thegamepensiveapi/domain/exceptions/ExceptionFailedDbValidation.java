package com.sethhaskellcondie.thegamepensiveapi.domain.exceptions;

import java.util.ArrayList;
import java.util.List;

public class ExceptionFailedDbValidation extends RuntimeException {
    private List<Exception> exceptions;

    public ExceptionFailedDbValidation() {
        super();
        this.exceptions = new ArrayList<>();
    }

    public ExceptionFailedDbValidation(String message) {
        super();
        this.exceptions = new ArrayList<>();
        exceptions.add(new Exception("Failed Database Validation: " + message));
    }

    public List<Exception> getExceptions() {
        return this.exceptions;
    }

    public void addException(String message) {
        this.exceptions.add(new Exception("Failed Database Validation: " + message));
    }

    @Override
    public String getMessage() {
        return String.join(" ", getMessages());
    }

    public List<String> getMessages() {
        List<String> errorMessages = new ArrayList<>();
        for (Exception e : this.exceptions) {
            errorMessages.add(e.getMessage());
        }
        return errorMessages;
    }
}
