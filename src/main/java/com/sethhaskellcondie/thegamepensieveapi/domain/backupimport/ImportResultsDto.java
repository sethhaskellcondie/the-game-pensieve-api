package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionBackupImport;

public record ImportResultsDto(int existingCustomFields, int createdCustomFields,
                               int existingToys, int createdToys,
                               int existingSystems, int createdSystems,
                               int existingVideoGames, int createdVideoGames,
                               ExceptionBackupImport exceptionBackupImport
) {
    ImportResultsDto(int existingCustomFields, int createdCustomFields, ExceptionBackupImport exceptionBackupImport) {
        this(existingCustomFields, createdCustomFields, 0, 0, 0, 0, 0, 0, exceptionBackupImport);
    }

    @Override
    public String toString() {
        String nonFormattedString = """
                {
                    existingCustomFields: %d,
                    createdCustomFields: %d,
                    existingToys: %d,
                    createdToys: %d,
                    existingSystems: %d,
                    createdSystems: %d,
                    existingVideoGames: %d,
                    createdVideoGames: %d,
                    exceptions: %d
                    exceptionMessages: %s
                }
                """;
        return String.format(
                nonFormattedString,
                this.existingCustomFields,
                this.createdCustomFields,
                this.existingToys,
                this.createdToys,
                this.existingSystems,
                this.createdSystems,
                this.existingVideoGames,
                this.createdVideoGames,
                this.exceptionBackupImport.getExceptions().size(),
                " \n - " + String.join(" \n - ", this.exceptionBackupImport.getMessages())
        );
    }
}
