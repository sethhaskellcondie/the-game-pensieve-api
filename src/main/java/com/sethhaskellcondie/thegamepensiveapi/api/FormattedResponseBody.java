package com.sethhaskellcondie.thegamepensiveapi.api;

import java.util.HashMap;
import java.util.Map;

public class FormattedResponseBody<T> {

    public T data;

    public FormattedResponseBody(T data) {
        this.data = data;
    }

    public Map<String, T> formatData() {
        Map<String, T> responseBody = new HashMap<>();
        responseBody.put("data", data);
        return responseBody;
    }

    public Map<String, T> formatError() {
        Map<String, T> responseBody = new HashMap<>();
        responseBody.put("error", data);
        return responseBody;
    }

}
