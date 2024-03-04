package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import org.springframework.http.HttpStatusCode;

public class ErrorResponse {
    private final String message;
    private final HttpStatusCode status;

    public ErrorResponse(String message, HttpStatusCode code) {
        this.message = message;
        this.status = code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}
