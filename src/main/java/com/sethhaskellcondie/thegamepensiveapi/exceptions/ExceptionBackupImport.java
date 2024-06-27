package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

import java.util.ArrayList;
import java.util.List;

public class ExceptionBackupImport extends RuntimeException {
    private final List<Exception> exceptions;

    public ExceptionBackupImport() {
        super();
        this.exceptions = new ArrayList<>();
    }

    public ExceptionBackupImport(String message) {
        super();
        this.exceptions = new ArrayList<>();
        exceptions.add(new Exception(Api.PRE_ERROR_MESSAGE + message));
    }

    public List<Exception> getExceptions() {
        return this.exceptions;
    }

    public void addException(Exception exception) {
        this.exceptions.add(exception);
    }

    public void appendExceptions(List<Exception> exceptions) {
        this.exceptions.addAll(exceptions);
    }

    @Override
    public String getMessage() {
        return String.join(" ", getMessages());
    }

    public List<String> getMessages() {
        List<String> errorMessages = new ArrayList<>();
        for (Exception e : this.exceptions) {
            errorMessages.add(e.getMessage());
        }
        return errorMessages;
    }
}
