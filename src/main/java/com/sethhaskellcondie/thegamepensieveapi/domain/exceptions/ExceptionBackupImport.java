package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

import java.util.ArrayList;
import java.util.List;

public class ExceptionBackupImport extends MultiException {

    private final MultiException customFieldExceptions;
    private final MultiException systemExceptions;
    private final MultiException videoGameBoxExceptions;
    private final MultiException videoGameExceptions;

    public ExceptionBackupImport() {
        super();
        this.messagePrefix = "Backup/Import Error - ";
        this.customFieldExceptions = new MultiException();
        this.systemExceptions = new MultiException();
        this.videoGameBoxExceptions = new MultiException();
        this.videoGameExceptions = new MultiException();
    }

    public ExceptionBackupImport(String message) {
        super();
        this.messagePrefix = "Backup/Import Error - ";
        this.customFieldExceptions = new MultiException();
        this.systemExceptions = new MultiException();
        this.videoGameBoxExceptions = new MultiException();
        this.videoGameExceptions = new MultiException();
        exceptions.add(new Exception(message));
    }

    public ExceptionBackupImport(List<Exception> exceptions) {
        super();
        this.messagePrefix = "Backup/Import Error - ";
        this.customFieldExceptions = new MultiException();
        this.systemExceptions = new MultiException();
        this.videoGameBoxExceptions = new MultiException();
        this.videoGameExceptions = new MultiException();
        this.exceptions = exceptions;
    }

    public MultiException getCustomFieldExceptions() {
        return customFieldExceptions;
    }

    public void addCustomFieldException(String message) {
        customFieldExceptions.addException(message);
    }

    public void addCustomFieldException(Exception exception) {
        customFieldExceptions.addException(exception);
    }

    public void addCustomFieldExceptions(List<Exception> exceptions) {
        customFieldExceptions.appendExceptions(exceptions);
    }

    public MultiException getSystemExceptions() {
        return systemExceptions;
    }

    public void addSystemException(String message) {
        systemExceptions.addException(message);
    }

    public void addSystemException(Exception exception) {
        systemExceptions.addException(exception);
    }

    public void addSystemExceptions(List<Exception> exceptions) {
        systemExceptions.appendExceptions(exceptions);
    }

    public MultiException getVideoGameBoxExceptions() {
        return videoGameBoxExceptions;
    }

    public void addVideoGameBoxException(String message) {
        videoGameBoxExceptions.addException(message);
    }

    public void addVideoGameBoxException(Exception exception) {
        videoGameBoxExceptions.addException(exception);
    }

    public void addVideoGameBoxExceptions(List<Exception> exceptions) {
        videoGameBoxExceptions.appendExceptions(exceptions);
    }

    public MultiException getVideoGameExceptions() {
        return videoGameExceptions;
    }

    public void addVideoGameException(String message) {
        videoGameExceptions.addException(message);
    }

    public void addVideoGameException(Exception exception) {
        videoGameExceptions.addException(exception);
    }

    public void addVideoGameExceptions(List<Exception> exceptions) {
        videoGameExceptions.appendExceptions(exceptions);
    }

    @Override
    public String getMessage() {
        StringBuilder messageBuilder = new StringBuilder();
        
        if (!this.exceptions.isEmpty()) {
            messageBuilder.append(super.getMessage());
        }
        
        if (!customFieldExceptions.isEmpty()) {
            if (!messageBuilder.isEmpty()) {
                messageBuilder.append(" ");
            }
            messageBuilder.append(customFieldExceptions.getMessage());
        }
        
        if (!systemExceptions.isEmpty()) {
            if (!messageBuilder.isEmpty()) {
                messageBuilder.append(" ");
            }
            messageBuilder.append(systemExceptions.getMessage());
        }
        
        if (!videoGameBoxExceptions.isEmpty()) {
            if (!messageBuilder.isEmpty()) {
                messageBuilder.append(" ");
            }
            messageBuilder.append(videoGameBoxExceptions.getMessage());
        }
        
        if (!videoGameExceptions.isEmpty()) {
            if (!messageBuilder.isEmpty()) {
                messageBuilder.append(" ");
            }
            messageBuilder.append(videoGameExceptions.getMessage());
        }
        
        return messageBuilder.toString();
    }

    @Override
    public List<String> getMessages() {
        List<String> allMessages = new ArrayList<>();
        
        if (!this.exceptions.isEmpty()) {
            allMessages.addAll(super.getMessages());
        }
        
        if (!customFieldExceptions.isEmpty()) {
            allMessages.addAll(customFieldExceptions.getMessages());
        }
        
        if (!systemExceptions.isEmpty()) {
            allMessages.addAll(systemExceptions.getMessages());
        }
        
        if (!videoGameBoxExceptions.isEmpty()) {
            allMessages.addAll(videoGameBoxExceptions.getMessages());
        }
        
        if (!videoGameExceptions.isEmpty()) {
            allMessages.addAll(videoGameExceptions.getMessages());
        }
        
        return allMessages;
    }

    @Override
    public List<Exception> getExceptions() {
        List<Exception> allExceptions = new ArrayList<>();
        
        if (!this.exceptions.isEmpty()) {
            allExceptions.addAll(super.getExceptions());
        }
        
        if (!customFieldExceptions.isEmpty()) {
            allExceptions.addAll(customFieldExceptions.getExceptions());
        }
        
        if (!systemExceptions.isEmpty()) {
            allExceptions.addAll(systemExceptions.getExceptions());
        }
        
        if (!videoGameBoxExceptions.isEmpty()) {
            allExceptions.addAll(videoGameBoxExceptions.getExceptions());
        }
        
        if (!videoGameExceptions.isEmpty()) {
            allExceptions.addAll(videoGameExceptions.getExceptions());
        }
        
        return allExceptions;
    }
}
