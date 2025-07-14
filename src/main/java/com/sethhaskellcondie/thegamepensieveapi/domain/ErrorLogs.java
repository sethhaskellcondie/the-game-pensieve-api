package com.sethhaskellcondie.thegamepensieveapi.domain;

public final class ErrorLogs {
    
    private ErrorLogs() {
        // Private constructor to prevent instantiation
    }
    
    public static String insertThenRetrieveError(String entityName, int id) {
        return "Database State Error: Just inserted a(n) " + entityName
                + " with the id " + id + " and couldn't immediately retrieve it.";
    }

    public static String updateThenRetrieveError(String entityName, int id) {
        return "Database State Error: Just updated a(n) " + entityName
                + " with the id " + id + " and couldn't immediately retrieve it.";
    }
}
