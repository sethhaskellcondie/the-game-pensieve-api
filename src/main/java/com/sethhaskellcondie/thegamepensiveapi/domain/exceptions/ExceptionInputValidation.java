package com.sethhaskellcondie.thegamepensiveapi.domain.exceptions;

public class ExceptionInputValidation extends RuntimeException {
    public ExceptionInputValidation(String message) {
        super("Failed Input Validation: " + message);
    }
}
