package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

/**
 * Raised when an authenticated request lacks the entitlement to perform an action — specifically, a GUEST or
 * LAPSED account attempting a write. Mapped to HTTP 403 (Forbidden) in {@code ApiControllerAdvice}.
 */
public class ExceptionForbidden extends MultiException {

    public ExceptionForbidden(String message) {
        super();
        this.messagePrefix = "Forbidden - ";
        exceptions.add(new Exception(messagePrefix + message));
    }
}
