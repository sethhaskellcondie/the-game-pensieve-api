package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import com.sethhaskellcondie.thegamepensiveapi.api.Api;

public class ExceptionResourceNotFound extends Exception {
	public ExceptionResourceNotFound(String message) {
		super(Api.PRE_ERROR_MESSAGE + "Resource Not Found: " + message);
	}
}
