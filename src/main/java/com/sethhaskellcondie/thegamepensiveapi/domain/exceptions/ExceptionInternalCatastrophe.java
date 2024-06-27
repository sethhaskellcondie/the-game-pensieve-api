package com.sethhaskellcondie.thegamepensiveapi.domain.exceptions;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

public class ExceptionInternalCatastrophe extends RuntimeException {
    public ExceptionInternalCatastrophe(String message) {
        super(Api.PRE_DISASTER_MESSAGE + "Internal Catastrophe: " + message);
    }

    public ExceptionInternalCatastrophe(String entityName, int id) {
        super(Api.PRE_DISASTER_MESSAGE + "Internal Catastrophe: Just inserted/updated a(n) " + entityName +
                " with the id " + id + " and couldn't immediately retrieve it.");
    }
}
