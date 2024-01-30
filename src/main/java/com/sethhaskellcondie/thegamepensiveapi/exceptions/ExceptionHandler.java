package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class ExceptionHandler {

	@org.springframework.web.bind.annotation.ExceptionHandler(value = {ExceptionFailedDbValidation.class})
	public ResponseEntity<Object> handleExceptionFailedDbValidation(ExceptionFailedDbValidation e) {
		HttpStatusCode code = HttpStatus.PRECONDITION_FAILED;
		ErrorResponse response = new ErrorResponse(e.getMessage(), code);
		return new ResponseEntity<>(response, code);
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(value = {ExceptionInputValidation.class})
	public ResponseEntity<Object> handleExceptionInputValidation(ExceptionInputValidation e) {
		HttpStatusCode code = HttpStatus.BAD_REQUEST;
		ErrorResponse response = new ErrorResponse(e.getMessage(), code);
		return new ResponseEntity<>(response, code);
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(value = {ExceptionResourceNotFound.class})
	public ResponseEntity<Object> handleExceptionResourceNotFound(ExceptionResourceNotFound e) {
		HttpStatusCode code = HttpStatus.NOT_FOUND;
		ErrorResponse response = new ErrorResponse(e.getMessage(), code);
		return new ResponseEntity<>(response, code);
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(value = {IllegalArgumentException.class})
	public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException e) {
		HttpStatusCode code = HttpStatus.BAD_REQUEST;
		ErrorResponse response = new ErrorResponse(e.getMessage(), code);
		return new ResponseEntity<>(response, code);
	}
}
