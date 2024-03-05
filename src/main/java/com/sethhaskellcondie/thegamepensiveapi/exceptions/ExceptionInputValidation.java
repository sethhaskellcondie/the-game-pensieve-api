package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

public class ExceptionInputValidation extends Exception {
    public ExceptionInputValidation(String message) {
        super(Api.PRE_ERROR_MESSAGE + "Failed Input Validation: " + message);
    }
}
