package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.BackupDataDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.BackupImportGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.ImportResultsDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.LocalBackupFileStore;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionImportInProgress;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restore from the working directory's backup.json, the other half of the local round trip described on
 * {@link LocalBackupFileStore}.
 *
 * <p>Gated to the default (unsecured, single-user) build, where the file has exactly one possible author. Under
 * {@code secured} this controller does not exist and the route 404s, because the file carries no record of which
 * tenant wrote it: importing it would pull whoever's collection happens to be on disk into the caller's account,
 * and every request involved would pass its capability check. Secured builds restore through
 * {@code POST /v1/function/import}, which takes the document in the request body.
 */
@RestController
@Profile("!secured")
public class LocalFileImportController extends BaseController {

    private final BackupImportGateway gateway;
    private final LocalBackupFileStore localBackupFileStore;

    public LocalFileImportController(BackupImportGateway gateway, LocalBackupFileStore localBackupFileStore) {
        this.gateway = gateway;
        this.localBackupFileStore = localBackupFileStore;
    }

    @PostMapping("v1/function/importFromFile")
    public ApiResponse<FormattedImportResultsData> importJsonFromFile(HttpServletRequest request) {
        if (!gateway.tryStartImport()) {
            throw new ExceptionImportInProgress();
        }
        try {
            final BackupDataDto backupData = localBackupFileStore.read();
            final ImportResultsDto importResults = gateway.importBackupData(backupData);
            final FormattedImportResultsData data = FormattedImportResultsData.from(importResults);
            return buildResponse(data, importResults.exceptionBackupImport().getMessages(), request);
        } finally {
            gateway.finishImport();
        }
    }
}
