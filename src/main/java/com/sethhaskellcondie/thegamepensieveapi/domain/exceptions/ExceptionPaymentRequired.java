package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

/**
 * Raised when a request needs an active subscription to proceed — specifically, a LAPSED account attempting a
 * filtered search. Mapped to HTTP 402 (Payment Required) in {@code ApiControllerAdvice}.
 */
public class ExceptionPaymentRequired extends MultiException {

    public ExceptionPaymentRequired(String message) {
        super();
        this.messagePrefix = "Payment Required - ";
        exceptions.add(new Exception(messagePrefix + message));
    }
}
