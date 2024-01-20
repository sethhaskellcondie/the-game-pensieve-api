package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

public class ExceptionResourceNotFound extends Exception {
	public ExceptionResourceNotFound(String message) {
		super(Api.PRE_ERROR_MESSAGE + "Resource Not Found: " + message);
	}

	public ExceptionResourceNotFound(String resourceName, int id) {
		super(Api.PRE_ERROR_MESSAGE + "Resource Not Found: " + resourceName +
			" not found in the database with id: " + id);
	}

	public ExceptionResourceNotFound(String message, String resourceName, int id) {
		super(Api.PRE_ERROR_MESSAGE + "Resource Not Found: " + message + " " +
			resourceName + " not found with id: " + id);
	}
}
