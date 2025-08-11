package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

import java.util.ArrayList;
import java.util.List;

public class ExceptionBackupImport extends MultiException {

    private String header;
    private final MultiException customFieldExceptions;
    private final MultiException toyExceptions;
    private final MultiException systemExceptions;
    private final MultiException videoGameBoxExceptions;
    private final MultiException videoGameExceptions;
    private final MultiException boardGameExceptions;
    private final MultiException boardGameBoxExceptions;

    public ExceptionBackupImport() {
        super();
        this.messagePrefix = "Backup/Import Error - ";
        this.header = "Unexpected Error on Backup/Import";
        this.customFieldExceptions = new MultiException();
        this.toyExceptions = new MultiException();
        this.systemExceptions = new MultiException();
        this.videoGameBoxExceptions = new MultiException();
        this.videoGameExceptions = new MultiException();
        this.boardGameExceptions = new MultiException();
        this.boardGameBoxExceptions = new MultiException();
    }

    public ExceptionBackupImport(String message) {
        super();
        this.messagePrefix = "Backup/Import Error - ";
        this.header = "Unexpected Error on Backup/Import";
        this.customFieldExceptions = new MultiException();
        this.toyExceptions = new MultiException();
        this.systemExceptions = new MultiException();
        this.videoGameBoxExceptions = new MultiException();
        this.videoGameExceptions = new MultiException();
        this.boardGameExceptions = new MultiException();
        this.boardGameBoxExceptions = new MultiException();
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
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

    // Toy Exception Methods
    public MultiException getToyExceptions() {
        return toyExceptions;
    }

    public void addToyException(String message) {
        toyExceptions.addException(message);
    }

    public void addToyException(Exception exception) {
        toyExceptions.addException(exception);
    }

    public void addToyExceptions(List<Exception> exceptions) {
        toyExceptions.appendExceptions(exceptions);
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

    public MultiException getBoardGameExceptions() {
        return boardGameExceptions;
    }

    public void addBoardGameException(String message) {
        boardGameExceptions.addException(message);
    }

    public void addBoardGameException(Exception exception) {
        boardGameExceptions.addException(exception);
    }

    public void addBoardGameExceptions(List<Exception> exceptions) {
        boardGameExceptions.appendExceptions(exceptions);
    }

    public MultiException getBoardGameBoxExceptions() {
        return boardGameBoxExceptions;
    }

    public void addBoardGameBoxException(String message) {
        boardGameBoxExceptions.addException(message);
    }

    public void addBoardGameBoxException(Exception exception) {
        boardGameBoxExceptions.addException(exception);
    }

    public void addBoardGameBoxExceptions(List<Exception> exceptions) {
        boardGameBoxExceptions.appendExceptions(exceptions);
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
        
        if (!toyExceptions.isEmpty()) {
            if (!messageBuilder.isEmpty()) {
                messageBuilder.append(" ");
            }
            messageBuilder.append(toyExceptions.getMessage());
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
        
        if (!boardGameExceptions.isEmpty()) {
            if (!messageBuilder.isEmpty()) {
                messageBuilder.append(" ");
            }
            messageBuilder.append(boardGameExceptions.getMessage());
        }
        
        if (!boardGameBoxExceptions.isEmpty()) {
            if (!messageBuilder.isEmpty()) {
                messageBuilder.append(" ");
            }
            messageBuilder.append(boardGameBoxExceptions.getMessage());
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
        
        if (!toyExceptions.isEmpty()) {
            allMessages.addAll(toyExceptions.getMessages());
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
        
        if (!boardGameExceptions.isEmpty()) {
            allMessages.addAll(boardGameExceptions.getMessages());
        }
        
        if (!boardGameBoxExceptions.isEmpty()) {
            allMessages.addAll(boardGameBoxExceptions.getMessages());
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
        
        if (!toyExceptions.isEmpty()) {
            allExceptions.addAll(toyExceptions.getExceptions());
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
        
        if (!boardGameExceptions.isEmpty()) {
            allExceptions.addAll(boardGameExceptions.getExceptions());
        }
        
        if (!boardGameBoxExceptions.isEmpty()) {
            allExceptions.addAll(boardGameBoxExceptions.getExceptions());
        }
        
        return allExceptions;
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty()
               && customFieldExceptions.isEmpty()
               && toyExceptions.isEmpty()
               && systemExceptions.isEmpty()
               && videoGameBoxExceptions.isEmpty()
               && videoGameExceptions.isEmpty()
               && boardGameExceptions.isEmpty()
               && boardGameBoxExceptions.isEmpty();
    }
}
