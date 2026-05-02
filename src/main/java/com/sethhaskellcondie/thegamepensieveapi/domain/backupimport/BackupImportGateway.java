package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class BackupImportGateway {
    private final BackupImportService service;
    private final AtomicBoolean importInProgress = new AtomicBoolean(false);

    public BackupImportGateway(BackupImportService service) {
        this.service = service;
    }

    public BackupDataDto getBackupData() {
        return service.getBackupData();
    }

    public boolean tryStartImport() {
        return importInProgress.compareAndSet(false, true);
    }

    public void finishImport() {
        importInProgress.set(false);
    }

    public ImportResultsDto importBackupData(BackupDataDto backupDataDto) {
        return service.importBackupData(backupDataDto);
    }
}
