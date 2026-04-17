package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;

public abstract class BaseController {

    protected <T> ApiResponse<T> buildResponse(T data, HttpServletRequest request) {
        return buildResponse(data, null, request);
    }

    protected <T> ApiResponse<T> buildResponse(T data, Object errors, HttpServletRequest request) {
        ApiResponse<T> response = new ApiResponse<>(data, errors);
        Object startTimeAttr = request.getAttribute("requestStartTime");
        if (startTimeAttr instanceof Long startTime) {
            response.setRoundTripMs(System.currentTimeMillis() - startTime);
        }
        return response;
    }
}
