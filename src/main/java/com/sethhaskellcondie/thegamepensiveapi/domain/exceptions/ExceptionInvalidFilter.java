package com.sethhaskellcondie.thegamepensiveapi.domain.exceptions;

import java.util.ArrayList;
import java.util.List;

public class ExceptionInvalidFilter extends MultiException {

    public ExceptionInvalidFilter() {
        super();
        this.messagePrefix = "Filter Error - ";
    }

    public ExceptionInvalidFilter(String message) {
        super();
        this.messagePrefix = "Filter Error - ";
        exceptions.add(new Exception(messagePrefix + message));
    }

    public ExceptionInvalidFilter(List<Exception> exceptions) {
        super();
        this.messagePrefix = "Filter Error - ";
        this.exceptions = exceptions;
    }
}
