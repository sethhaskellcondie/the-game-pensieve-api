package com.sethhaskellcondie.thegamepensiveapi.domain.exceptions;

import java.util.ArrayList;
import java.util.List;

public class ExceptionMalformedEntity extends RuntimeException {
    private final List<Exception> exceptions;

    public ExceptionMalformedEntity(List<Exception> exceptions) {
        super();
        this.exceptions = exceptions;
    }

    public List<Exception> getExceptions() {
        return this.exceptions;
    }

    public List<String> getMessages() {
        List<String> errorMessages = new ArrayList<>();
        for (Exception e : this.exceptions) {
            errorMessages.add(e.getMessage());
        }
        return errorMessages;
    }
}
