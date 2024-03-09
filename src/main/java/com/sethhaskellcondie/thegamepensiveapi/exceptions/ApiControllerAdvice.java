package com.sethhaskellcondie.thegamepensiveapi.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

import static java.util.Collections.singletonMap;

@ControllerAdvice
public class ApiControllerAdvice {

    @ExceptionHandler(value = {ExceptionFailedDbValidation.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public String handleExceptionFailedDbValidation(ExceptionFailedDbValidation e) {
        return e.getMessage();
    }

    @ExceptionHandler(value = {ExceptionInputValidation.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public String handleExceptionInputValidation(ExceptionInputValidation e) {
        return e.getMessage();
    }

    @ExceptionHandler(value = {ExceptionResourceNotFound.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public String handleExceptionResourceNotFound(ExceptionResourceNotFound e) {
        return e.getMessage();
    }

    @ExceptionHandler(value = {IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public String handleIllegalArgumentException(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(value = {ExceptionMalformedEntity.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, Object> handleExceptionMalformedEntity(ExceptionMalformedEntity e) {
        return singletonMap("errors", e.getErrors());
    }
}
