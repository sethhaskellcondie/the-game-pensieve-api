package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

public class ExceptionInternalError extends RuntimeException {
    public ExceptionInternalError(String message) {
        super("Internal Error: " + message);
    }
}
