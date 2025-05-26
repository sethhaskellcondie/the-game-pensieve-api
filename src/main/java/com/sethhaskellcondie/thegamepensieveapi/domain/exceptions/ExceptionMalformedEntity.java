package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

import java.util.List;

public class ExceptionMalformedEntity extends MultiException {

    public ExceptionMalformedEntity() {
        super();
        this.messagePrefix = "Malformed Entity Error - ";
    }

    public ExceptionMalformedEntity(String message) {
        super();
        this.messagePrefix = "Malformed Entity Error - ";
        exceptions.add(new Exception(messagePrefix + message));
    }

    public ExceptionMalformedEntity(List<Exception> exceptions) {
        super();
        this.messagePrefix = "Malformed Entity Error - ";
        this.exceptions = exceptions;
    }
}
