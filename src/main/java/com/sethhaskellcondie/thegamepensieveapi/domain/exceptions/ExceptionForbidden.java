package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

/**
 * Raised when an authenticated request's role lacks the capability for an action — e.g. a LAPSED account
 * attempting a write or import, or a non-ADMIN reaching the admin API. Mapped to HTTP 403 (Forbidden) in
 * {@code ApiControllerAdvice}.
 */
public class ExceptionForbidden extends MultiException {

    public ExceptionForbidden(String message) {
        super();
        this.messagePrefix = "Forbidden - ";
        exceptions.add(new Exception(messagePrefix + message));
    }
}
