package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

public class ExceptionInternalCatastrophe extends RuntimeException {
    public ExceptionInternalCatastrophe(String message) {
        super("Internal Catastrophe: " + message);
    }

    public ExceptionInternalCatastrophe(String entityName, int id) {
        super("Internal Catastrophe: Just inserted/updated a(n) " + entityName +
                " with the id " + id + " and couldn't immediately retrieve it.");
    }

    public ExceptionInternalCatastrophe(String entityName, int id, Throwable cause) {
        super("Internal Catastrophe: Just inserted/updated a(n) " + entityName +
                " with the id " + id + " and couldn't immediately retrieve it.", cause);
    }
}
