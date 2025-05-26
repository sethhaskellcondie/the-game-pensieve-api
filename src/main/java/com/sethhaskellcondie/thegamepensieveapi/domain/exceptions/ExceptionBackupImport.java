package com.sethhaskellcondie.thegamepensieveapi.domain.exceptions;

import java.util.List;

public class ExceptionBackupImport extends MultiException {

    public ExceptionBackupImport() {
        super();
        this.messagePrefix = "Backup/Import Error - ";
    }

    public ExceptionBackupImport(String message) {
        super();
        this.messagePrefix = "Backup/Import Error - ";
        exceptions.add(new Exception(message));
    }

    public ExceptionBackupImport(List<Exception> exceptions) {
        super();
        this.messagePrefix = "Backup/Import Error - ";
        this.exceptions = exceptions;
    }
}
