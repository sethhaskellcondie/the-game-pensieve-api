package com.sethhaskellcondie.thegamepensiveapi.domain.exceptions;

public class ExceptionCustomFieldValue extends RuntimeException {
    public ExceptionCustomFieldValue(String message) {
        super("Custom Field Value Exception: " + message);
    }
}
