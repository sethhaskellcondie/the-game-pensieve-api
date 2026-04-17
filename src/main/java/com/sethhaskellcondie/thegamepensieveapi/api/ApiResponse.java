package com.sethhaskellcondie.thegamepensieveapi.api;

public class ApiResponse<T> {
    private T data;
    private Object errors;
    private Long roundTripMs;

    public ApiResponse(T data, Object errors) {
        this.data = data;
        this.errors = errors;
    }

    public T getData() {
        return data;
    }

    public Object getErrors() {
        return errors;
    }

    public Long getRoundTripMs() {
        return roundTripMs;
    }

    public void setRoundTripMs(Long roundTripMs) {
        this.roundTripMs = roundTripMs;
    }
}
