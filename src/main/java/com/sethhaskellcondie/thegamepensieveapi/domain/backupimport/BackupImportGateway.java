package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AccessService;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Capability;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionForbidden;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class BackupImportGateway {
    private final BackupImportService service;
    private final AccessService access;
    private final AtomicBoolean importInProgress = new AtomicBoolean(false);

    public BackupImportGateway(BackupImportService service, AccessService access) {
        this.service = service;
        this.access = access;
    }

    public BackupDataDto getBackupData() {
        if (!access.can(Capability.BACKUP)) {
            throw new ExceptionForbidden("Permission denied, backup access required.");
        }
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
