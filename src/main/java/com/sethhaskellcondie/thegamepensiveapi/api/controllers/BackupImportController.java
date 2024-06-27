package com.sethhaskellcondie.thegamepensiveapi.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.api.FormattedResponseBody;
import com.sethhaskellcondie.thegamepensiveapi.domain.backupimport.BackupDataDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.backupimport.BackupImportGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.backupimport.ImportResultsDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * There are no api tests for these endpoint, instead there are domain tests for the backupImportGateway
 */
@RestController
public class BackupImportController {

    private final BackupImportGateway gateway;
    private final String backupDataPath = "backup.json";

    public BackupImportController(BackupImportGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("v1/function/backup")
    public Map<String, FormattedBackupData> backupJsonToFile() {
        final File file = new File(backupDataPath);
        final ObjectMapper objectMapper = new ObjectMapper();
        final BackupDataDto backupDataDto = gateway.getBackupData();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, backupDataDto);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final FormattedResponseBody<FormattedBackupData> body = new FormattedResponseBody<>(new FormattedBackupData(file.getAbsolutePath(), backupDataDto));
        return body.formatData();
    }

    @PostMapping("v1/function/importFromFile")
    public FormattedImportResults importJsonFromFile() {
        final BackupDataDto backupData;
        try {
            final byte[] fileData = Files.readAllBytes(Paths.get(backupDataPath));
            final ObjectMapper objectMapper = new ObjectMapper();
            backupData = objectMapper.readValue(fileData, BackupDataDto.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final ImportResultsDto importResults = gateway.importBackupData(backupData);
        final FormattedImportResultsData data = new FormattedImportResultsData(
                importResults.existingCustomFields(), importResults.createdCustomFields(),
                importResults.existingToys(), importResults.createdToys(),
                importResults.existingSystems(), importResults.createdSystems()
                );
        return new FormattedImportResults(data, importResults.exceptionBackupImport().getMessages());
    }

    @PostMapping("v1/function/import")
    public FormattedImportResults importJsonFromRequestBody(@RequestBody Map<String, BackupDataDto> requestBody) {
        final BackupDataDto backupData = requestBody.get("data");

        final ImportResultsDto importResults = gateway.importBackupData(backupData);
        final FormattedImportResultsData data = new FormattedImportResultsData(
                importResults.existingCustomFields(), importResults.createdCustomFields(),
                importResults.existingToys(), importResults.createdToys(),
                importResults.existingSystems(), importResults.createdSystems()
        );
        return new FormattedImportResults(data, importResults.exceptionBackupImport().getMessages());
    }

    @PostMapping("v1/function/seedSampleData")
    public FormattedImportResults seedSampleData() {
        final BackupDataDto sampleData;
        try {
            final byte[] fileData = Files.readAllBytes(Paths.get("sampleData.json"));
            final ObjectMapper objectMapper = new ObjectMapper();
            sampleData = objectMapper.readValue(fileData, BackupDataDto.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final ImportResultsDto importResults = gateway.importBackupData(sampleData);
        final FormattedImportResultsData data = new FormattedImportResultsData(
                importResults.existingCustomFields(), importResults.createdCustomFields(),
                importResults.existingToys(), importResults.createdToys(),
                importResults.existingSystems(), importResults.createdSystems()
        );
        return new FormattedImportResults(data, importResults.exceptionBackupImport().getMessages());
    }

    //TODO seed my collection endpoint
}

record FormattedBackupData(String filePath, BackupDataDto data) { }
record FormattedImportResultsData(int existingCustomFields, int createdCustomFields, int existingToys, int createdToys, int existingSystems, int createdSystems) { }
record FormattedImportResults(FormattedImportResultsData data, List<String> errors) { }
