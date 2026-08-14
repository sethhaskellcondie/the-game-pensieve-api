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
        if (!access.can(Capability.IMPORT)) {
            throw new ExceptionForbidden("Permission denied, import access required.");
        }
        return service.importBackupData(backupDataDto);
    }

    /**
     * Import one of the fixture files bundled in the image. Mechanically identical to
     * {@link #importBackupData(BackupDataDto)}, but gated on SEED rather than IMPORT: the data is the
     * maintainer's, not the caller's, so seeding is an ADMIN tool and not something an IMPORT-capable customer
     * should be able to fire into their own collection. Enforcement is off in the default permit-all build, so a
     * local single-user instance seeds without restriction.
     */
    public ImportResultsDto importSeedData(BackupDataDto seedData) {
        if (!access.can(Capability.SEED)) {
            throw new ExceptionForbidden("Permission denied, admin access required to seed bundled data.");
        }
        return service.importBackupData(seedData);
    }
}
