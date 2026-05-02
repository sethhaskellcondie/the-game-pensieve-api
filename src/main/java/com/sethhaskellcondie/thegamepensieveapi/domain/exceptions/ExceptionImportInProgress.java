package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

public class ExceptionImportInProgress extends RuntimeException {
    public ExceptionImportInProgress() {
        super("An import is already in progress. Please wait for it to complete before starting another.");
    }
}
