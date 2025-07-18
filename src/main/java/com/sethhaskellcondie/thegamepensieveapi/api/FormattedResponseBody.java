package com.sethhaskellcondie.thegamepensieveapi.api;

import java.util.HashMap;
import java.util.Map;

public class FormattedResponseBody<T> {

    private T data;

    public FormattedResponseBody(T data) {
        this.data = data;
    }

    public Map<String, T> formatData() {
        Map<String, T> responseBody = new HashMap<>();
        responseBody.put("data", data);
        responseBody.put("errors", null);
        return responseBody;
    }

    public Map<String, T> formatError() {
        Map<String, T> responseBody = new HashMap<>();
        responseBody.put("data", null);
        responseBody.put("errors", data);
        return responseBody;
    }

}
