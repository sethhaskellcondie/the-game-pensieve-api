package com.sethhaskellcondie.thegamepensiveapi.domain.exceptions;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

public class ExceptionInputValidation extends RuntimeException {
    public ExceptionInputValidation(String message) {
        super(Api.PRE_ERROR_MESSAGE + "Failed Input Validation: " + message);
    }
}
