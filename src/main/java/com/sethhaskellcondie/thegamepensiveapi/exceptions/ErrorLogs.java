package com.sethhaskellcondie.thegamepensiveapi.exceptions;

public class ErrorLogs {
	public static String InsertThenRetrieveError(String entityName, int id) {
		return "Database State Error: Just inserted a(n) " + entityName +
			" with the id " + id + " and couldn't immediately retrieve it.";
	}

	public static String UpdateThenRetrieveError(String entityName, int id) {
		return "Database State Error: Just updated a(n) " + entityName +
			" with the id " + id + " and couldn't immediately retrieve it.";
	}
}
