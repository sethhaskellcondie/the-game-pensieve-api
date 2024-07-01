package com.sethhaskellcondie.thegamepensiveapi.domain.exceptions;

import java.util.List;

public class ExceptionFailedDbValidation extends MultiException {

    public ExceptionFailedDbValidation() {
        super();
        this.messagePrefix = "Failed Database Validation - ";
    }

    public ExceptionFailedDbValidation(String message) {
        super();
        this.messagePrefix = "Failed Database Validation - ";
        exceptions.add(new Exception(messagePrefix + message));
    }

    public ExceptionFailedDbValidation(List<Exception> exceptions) {
        super();
        this.messagePrefix = "Failed Database Validation - ";
        this.exceptions = exceptions;
    }
}
