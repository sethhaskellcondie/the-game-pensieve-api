package com.sethhaskellcondie.thegamepensiveapi.api;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

public class ErrorLogs {
    public static String InsertThenRetrieveError(String entityName, int id) {
        return Api.PRE_DISASTER_MESSAGE + "Database State Error: Just inserted a(n) " + entityName +
                " with the id " + id + " and couldn't immediately retrieve it.";
    }

    public static String UpdateThenRetrieveError(String entityName, int id) {
        return Api.PRE_DISASTER_MESSAGE + "Database State Error: Just updated a(n) " + entityName +
                " with the id " + id + " and couldn't immediately retrieve it.";
    }
}
