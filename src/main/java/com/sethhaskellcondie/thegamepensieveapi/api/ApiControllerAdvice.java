package com.sethhaskellcondie.thegamepensieveapi.api;

import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionBackupImport;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionCustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionImportInProgress;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionForbidden;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInvalidFilter;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionPaymentRequired;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ControllerAdvice
public class ApiControllerAdvice {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiControllerAdvice.class);

    /**
     * What an unexpected 500 tells the caller. Deliberately says nothing about the failure: these two
     * handlers catch anything the application did not anticipate, and the most common thing to land here is
     * a Postgres {@code DataAccessException} whose message carries the failing SQL, the constraint name, and
     * the internal hostname {@code db:5432}. The showcase read surface is reachable WITHOUT a token, so that
     * message was being handed to anonymous callers.
     * <p>
     * The detail is not lost — it goes to the log with a correlation id, and the same id goes to the caller.
     * "Something broke, here is the id" is enough for a user to report and enough for you to grep for.
     */
    private static final String GENERIC_500_MESSAGE = "Something went wrong. The error has been logged; quote the reference id if you report it.";

    //----Handle General Errors----
    @ExceptionHandler(value = {Exception.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Map<String, List<String>> handleGeneralException(Exception e) {
        return logAndFormat("Generic Exception Caught", e);
    }


    @ExceptionHandler(value = {RuntimeException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Map<String, List<String>> handleRuntimeException(RuntimeException e) {
        return logAndFormat("Generic RuntimeException Caught", e);
    }

    /**
     * Log the real exception against a fresh correlation id and return only that id to the caller. The id is
     * a random UUID rather than anything derived from the request, so it leaks nothing on its own.
     */
    private Map<String, List<String>> logAndFormat(String context, Exception e) {
        final String errorId = UUID.randomUUID().toString();
        LOGGER.error("Unhandled exception [errorId={}] ({})", errorId, context, e);
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(
                List.of(GENERIC_500_MESSAGE, "Reference id: " + errorId));
        return body.formatError();
    }

    //----Handle Authentication Errors----
    @ExceptionHandler(value = {AuthenticationException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ResponseBody
    public Map<String, List<String>> handleAuthenticationException(AuthenticationException e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(List.of(e.getMessage()));
        return body.formatError();
    }

    //----Handle Access/Capability Errors----
    @ExceptionHandler(value = {ExceptionPaymentRequired.class})
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    @ResponseBody
    public Map<String, List<String>> handleExceptionPaymentRequired(ExceptionPaymentRequired e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(e.getMessages());
        return body.formatError();
    }

    @ExceptionHandler(value = {ExceptionForbidden.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    public Map<String, List<String>> handleExceptionForbidden(ExceptionForbidden e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(e.getMessages());
        return body.formatError();
    }

    //----Handle Entity Specific Errors----
    @ExceptionHandler(value = {ExceptionImportInProgress.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public Map<String, List<String>> handleExceptionImportInProgress(ExceptionImportInProgress e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(List.of(e.getMessage()));
        return body.formatError();
    }

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

    @ExceptionHandler(value = {NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public Map<String, List<String>> handleNoResourceFoundException(NoResourceFoundException e) {
        FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(List.of(e.getMessage()));
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
