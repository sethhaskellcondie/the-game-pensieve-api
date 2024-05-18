package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

public class ExceptionInternalError extends RuntimeException {
    public ExceptionInternalError(String message) {
        super(Api.PRE_ERROR_MESSAGE + "Internal Error: " + message);
    }
}
