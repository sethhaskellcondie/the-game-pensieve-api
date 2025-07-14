package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

import java.util.ArrayList;
import java.util.List;

public class MultiException extends RuntimeException {
    protected List<Exception> exceptions;
    protected String messagePrefix = "Error! - ";

    public MultiException() {
        super();
        this.exceptions = new ArrayList<>();
    }

    public MultiException(String message) {
        super();
        this.exceptions = new ArrayList<>();
        exceptions.add(new Exception(messagePrefix + message));
    }

    public MultiException(List<Exception> exceptions) {
        super();
        this.exceptions = exceptions;
    }

    public List<Exception> getExceptions() {
        return exceptions;
    }

    public boolean isEmpty() {
        return exceptions.isEmpty();
    }

    public void addException(String message) {
        exceptions.add(new Exception(messagePrefix + message));
    }

    public void appendExceptions(List<Exception> exceptionList) {
        this.exceptions.addAll(exceptionList);
    }

    public void addException(Exception e) {
        exceptions.add(e);
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
