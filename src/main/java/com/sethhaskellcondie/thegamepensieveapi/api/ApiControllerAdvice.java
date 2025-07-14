package com.sethhaskellcondie.thegamepensieveapi.api;

import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionBackupImport;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionCustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInvalidFilter;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Map;

@ControllerAdvice
public class ApiControllerAdvice {

    //----Handle General Errors----
    @ExceptionHandler(value = {Exception.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Map<String, List<String>> handleGeneralException(Exception e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(List.of("Something went wrong. Generic Exception Caught.", e.getMessage()));
        return body.formatError();
    }


    @ExceptionHandler(value = {RuntimeException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Map<String, List<String>> handleRuntimeException(RuntimeException e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(List.of("Something went wrong. Generic RuntimeException Caught.", e.getMessage()));
        return body.formatError();
    }

    //----Handle Entity Specific Errors----
    @ExceptionHandler(value = {ExceptionCustomFieldValue.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, List<String>> handleExceptionCustomFieldValue(ExceptionCustomFieldValue e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(List.of(e.getMessage()));
        return body.formatError();
    }

    @ExceptionHandler(value = {ExceptionFailedDbValidation.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, List<String>> handleExceptionFailedDbValidation(ExceptionFailedDbValidation e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(e.getMessages());
        return body.formatError();
    }

    @ExceptionHandler(value = {ExceptionInputValidation.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, List<String>> handleExceptionInputValidation(ExceptionInputValidation e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(List.of(e.getMessage()));
        return body.formatError();
    }

    @ExceptionHandler(value = {ExceptionInvalidFilter.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, List<String>> handleExceptionInvalidFilter(ExceptionInvalidFilter e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(e.getMessages());
        return body.formatError();
    }

    @ExceptionHandler(value = {ExceptionMalformedEntity.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, List<String>> handleExceptionMalformedEntity(ExceptionMalformedEntity e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(e.getMessages());
        return body.formatError();
    }

    @ExceptionHandler(value = {ExceptionResourceNotFound.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public Map<String, List<String>> handleExceptionResourceNotFound(ExceptionResourceNotFound e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(List.of(e.getMessage()));
        return body.formatError();
    }

    @ExceptionHandler(value = {ExceptionBackupImport.class})
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Map<String, List<String>> handleExceptionBackupRestore(ExceptionBackupImport e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(e.getMessages());
        return body.formatError();
    }
}
