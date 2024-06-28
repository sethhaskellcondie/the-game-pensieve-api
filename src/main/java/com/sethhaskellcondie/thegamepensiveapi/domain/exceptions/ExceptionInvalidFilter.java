package com.sethhaskellcondie.thegamepensiveapi.domain.exceptions;

import java.util.ArrayList;
import java.util.List;

public class ExceptionInvalidFilter extends RuntimeException {
    private final List<Exception> exceptions;

    public ExceptionInvalidFilter() {
        this.exceptions = new ArrayList<>();
    }

    public void addException(String message) {
        exceptions.add(new Exception("Exception Invalid Filter: " + message));
    }

    public List<Exception> getExceptions() {
        return this.exceptions;
    }

    public boolean exceptionsFound() {
        return !exceptions.isEmpty();
    }

    public List<String> getMessages() {
        List<String> errorMessages = new ArrayList<>();
        for (Exception e : this.exceptions) {
            errorMessages.add(e.getMessage());
        }
        return errorMessages;
    }
}
