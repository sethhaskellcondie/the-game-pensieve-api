package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import org.springframework.stereotype.Component;

@Component
public class BackupImportGateway {
    private final BackupImportService service;

    public BackupImportGateway(BackupImportService service) {
        this.service = service;
    }

    public BackupDataDto getBackupData() {
        return service.getBackupData();
    }

    public ImportResultsDto importBackupData(BackupDataDto backupDataDto) {
        return service.importBackupData(backupDataDto);
    }
}
