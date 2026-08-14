package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.BackupDataDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.BackupImportGateway;
import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.ImportResultsDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.backupimport.LocalBackupFileStore;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionImportInProgress;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInternalError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * There are no api tests for these endpoints, instead there are domain tests for the backupImportGateway
 */
@RestController
public class BackupImportController extends BaseController {

    private final BackupImportGateway gateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Present only in the default (unsecured, single-user) build — see {@link LocalBackupFileStore} for why a
     * hosted build must not write backups to a shared file. When it is absent, {@code /v1/function/backup}
     * returns the export in the response body and writes nothing to disk.
     */
    private final Optional<LocalBackupFileStore> localBackupFileStore;

    public BackupImportController(BackupImportGateway gateway, Optional<LocalBackupFileStore> localBackupFileStore) {
        this.gateway = gateway;
        this.localBackupFileStore = localBackupFileStore;
    }

    @PostMapping("v1/function/backup")
    public ApiResponse<BackupDataDto> backupJson(HttpServletRequest request) {
        final BackupDataDto backupDataDto = gateway.getBackupData();
        localBackupFileStore.ifPresent(store -> store.write(backupDataDto));
        return buildResponse(backupDataDto, request);
    }

    @PostMapping("v1/function/import")
    public ApiResponse<FormattedImportResultsData> importJsonFromRequestBody(@RequestBody Map<String, BackupDataDto> requestBody, HttpServletRequest request) {
        if (!gateway.tryStartImport()) {
            throw new ExceptionImportInProgress();
        }
        try {
            final BackupDataDto backupData = requestBody.get("data");
            final ImportResultsDto importResults = gateway.importBackupData(backupData);
            final FormattedImportResultsData data = FormattedImportResultsData.from(importResults);
            return buildResponse(data, importResults.exceptionBackupImport().getMessages(), request);
        } finally {
            gateway.finishImport();
        }
    }

    @PostMapping("v1/function/seedSampleData")
    public ApiResponse<FormattedImportResultsData> seedSampleData(HttpServletRequest request) {
        return seedFromBundledFile("sampleData.json", request);
    }

    @PostMapping("v1/function/seedMyCollection")
    public ApiResponse<FormattedImportResultsData> seedMyCollection(HttpServletRequest request) {
        return seedFromBundledFile("myCollection.json", request);
    }

    /**
     * The two seed endpoints import a fixture file that ships in the image rather than caller-supplied data, so
     * they route through {@code importSeedData} and require the SEED capability (ADMIN only) instead of IMPORT.
     * A paying user has no reason to bulk-inject the maintainer's fixtures into their own collection; enforcement
     * is off in the default build, so a local single-user instance keeps seeding freely.
     */
    private ApiResponse<FormattedImportResultsData> seedFromBundledFile(String fileName, HttpServletRequest request) {
        if (!gateway.tryStartImport()) {
            throw new ExceptionImportInProgress();
        }
        try {
            final BackupDataDto seedData;
            try {
                final byte[] fileData = Files.readAllBytes(Paths.get(fileName));
                seedData = objectMapper.readValue(fileData, BackupDataDto.class);
            } catch (IOException e) {
                throw new ExceptionInternalError("Failed to read seed data from file: " + fileName, e);
            }
            final ImportResultsDto importResults = gateway.importSeedData(seedData);
            final FormattedImportResultsData data = FormattedImportResultsData.from(importResults);
            return buildResponse(data, importResults.exceptionBackupImport().getMessages(), request);
        } finally {
            gateway.finishImport();
        }
    }
}

record FormattedImportResultsData(int existingCustomFields, int createdCustomFields, int existingToys, int createdToys,
                                  int existingSystems, int createdSystems, int existingVideoGameBoxes, int createdVideoGameBoxes,
                                  int existingBoardGameBoxes, int createdBoardGameBoxes,
                                  int existingMetadata, int createdMetadata) {

    static FormattedImportResultsData from(ImportResultsDto importResults) {
        return new FormattedImportResultsData(
                importResults.existingCustomFields(), importResults.createdCustomFields(),
                importResults.existingToys(), importResults.createdToys(),
                importResults.existingSystems(), importResults.createdSystems(),
                importResults.existingVideoGamesBoxes(), importResults.createdVideoGamesBoxes(),
                importResults.existingBoardGameBoxes(), importResults.createdBoardGameBoxes(),
                importResults.existingMetadata(), importResults.createdMetadata()
        );
    }
}
