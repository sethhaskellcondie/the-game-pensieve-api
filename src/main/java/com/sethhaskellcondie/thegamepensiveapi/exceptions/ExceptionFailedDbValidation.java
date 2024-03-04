package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

public class ExceptionFailedDbValidation extends Exception {
    public ExceptionFailedDbValidation(String message) {
        super(Api.PRE_ERROR_MESSAGE + "Failed Database Validation: " + message);
    }
}
