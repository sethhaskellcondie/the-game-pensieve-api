package com.sethhaskellcondie.thegamepensiveapi.domain.backupimport;

import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionBackupImport;

public record ImportResultsDto(int existingCustomFields, int createdCustomFields,
                               int existingToys, int createdToys,
                               int existingSystems, int createdSystems,
                               ExceptionBackupImport exceptionBackupImport
) {
    ImportResultsDto(int existingCustomFields, int createdCustomFields, ExceptionBackupImport exceptionBackupImport) {
        this(existingCustomFields, createdCustomFields, 0, 0, 0, 0, exceptionBackupImport);
    }
}
