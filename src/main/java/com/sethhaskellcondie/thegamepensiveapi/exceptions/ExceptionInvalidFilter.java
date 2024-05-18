package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

import java.util.ArrayList;
import java.util.List;

public class ExceptionInvalidFilter extends RuntimeException {
    private final List<Exception> exceptions;

    public ExceptionInvalidFilter() {
        this.exceptions = new ArrayList<>();
    }

    public void addException(String message) {
        exceptions.add(new Exception(Api.PRE_ERROR_MESSAGE + "Exception Invalid Filter: " + message));
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
