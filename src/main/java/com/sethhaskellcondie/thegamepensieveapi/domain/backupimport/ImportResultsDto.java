package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionBackupImport;

public record ImportResultsDto(int existingCustomFields, int createdCustomFields,
                               int existingToys, int createdToys,
                               int existingSystems, int createdSystems,
                               int existingVideoGamesBoxes, int createdVideoGamesBoxes,
                               int existingBoardGameBoxes, int createdBoardGameBoxes,
                               ExceptionBackupImport exceptionBackupImport
) {
    ImportResultsDto(int existingCustomFields, int createdCustomFields, ExceptionBackupImport exceptionBackupImport) {
        this(existingCustomFields, createdCustomFields, 0, 0, 0, 0, 0, 0, 0, 0, exceptionBackupImport);
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
                    existingVideoGamesBoxes: %d,
                    createdVideoGamesBoxes: %d,
                    existingBoardGameBoxes: %d,
                    createdBoardGameBoxes: %d,
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
                this.existingVideoGamesBoxes,
                this.createdVideoGamesBoxes,
                this.existingBoardGameBoxes,
                this.createdBoardGameBoxes,
                this.exceptionBackupImport.getExceptions().size(),
                " \n - " + String.join(" \n - ", this.exceptionBackupImport.getMessages())
        );
    }
}
