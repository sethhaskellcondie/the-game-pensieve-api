package com.sethhaskellcondie.thegamepensiveapi.domain.exceptions;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

public class ExceptionCustomFieldValue extends RuntimeException {
    public ExceptionCustomFieldValue(String message) {
        super(Api.PRE_ERROR_MESSAGE + "Custom Field Value Exception: " + message);
    }
}
