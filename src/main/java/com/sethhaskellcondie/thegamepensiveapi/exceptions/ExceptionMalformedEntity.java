package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import java.util.List;

public class ExceptionMalformedEntity extends RuntimeException {
    private final List<Exception> errors;

    public ExceptionMalformedEntity(List<Exception> errors) {
        super();
        this.errors = errors;
    }

    public List<Exception> getErrors() {
        return errors;
    }
}
