package com.sethhaskellcondie.thegamepensiveapi.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyResponseDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class has no automated tests, they will be tested and updated manually.
 */
@RestController
public class BackupRestoreController {

    private final SystemGateway systemGateway;
    private final ToyGateway toyGateway;
    private final CustomFieldRepository customFieldRepository;

    public BackupRestoreController(SystemGateway systemGateway, ToyGateway toyGateway, CustomFieldRepository customFieldRepository) {
        this.systemGateway = systemGateway;
        this.toyGateway = toyGateway;
        this.customFieldRepository = customFieldRepository;
    }

    @PostMapping("v1/function/backup")
    public Map<String, String> backupJsonToFile() {
        List<BackupCustomField> customFields = customFieldRepository.getAllCustomFields().stream().map(BackupCustomField::covertToBackupData).toList();
        List<BackupToy> toys = toyGateway.getWithFilters(new ArrayList<>()).stream().map(BackupToy::convertToBackupData).toList();
        List<BackupSystem> systems = systemGateway.getWithFilters(new ArrayList<>()).stream().map(BackupSystem::convertToBackupData).toList();

        FormattedBackupData backupData = new FormattedBackupData("backupData", customFields, toys, systems);
        ObjectMapper objectMapper = new ObjectMapper();

        File file = new File("backup.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, backupData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final FormattedResponseBody<String> body = new FormattedResponseBody<>("JSON Backup Successful, File saved to: " + file.getAbsolutePath());
        return body.formatData();
    }

    @PostMapping("v1/function/restore")
    public Map<String, String> restoreJsonFromFile() {
        //TODO finish this
        return null;
    }
}

record FormattedBackupData(String dataType, List<BackupCustomField> customFields, List<BackupToy> toys, List<BackupSystem> systems) { }

record BackupCustomField(String name, String type, String entityKey) {
    public static BackupCustomField covertToBackupData(CustomField customField) {
        return new BackupCustomField(customField.name(), customField.type(), customField.entityKey());
    }
}

record BackupToy(String name, String set, List<CustomFieldValue> customFieldValues) {
    public static BackupToy convertToBackupData(ToyResponseDto toy) {
        return new BackupToy(toy.name(), toy.set(), toy.customFieldValues());
    }
}

record BackupSystem(String name, int generation, boolean handheld, List<CustomFieldValue> customFieldValues) {
    public static BackupSystem convertToBackupData(SystemResponseDto system) {
        return new BackupSystem(system.name(), system.generation(), system.handheld(), system.customFieldValues());
    }
}
