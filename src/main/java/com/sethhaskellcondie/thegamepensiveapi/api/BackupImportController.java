package com.sethhaskellcondie.thegamepensiveapi.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionBackupImport;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class has no automated tests, they will be tested and updated manually.
 * <p>
 * This class acts like another system that is trying to interact with the domain. It can only use the DTO objects (not the entities)
 * and only has access to the gateways (except the CustomFieldRepository)
 */
@RestController
public class BackupImportController {

    private final SystemGateway systemGateway;
    private final ToyGateway toyGateway;
    private final CustomFieldRepository customFieldRepository;
    private final String backupDataPath = "backup.json";

    public BackupImportController(SystemGateway systemGateway, ToyGateway toyGateway, CustomFieldRepository customFieldRepository) {
        this.systemGateway = systemGateway;
        this.toyGateway = toyGateway;
        this.customFieldRepository = customFieldRepository;
    }

    @PostMapping("v1/function/seedSampleData")
    public Map<String, String> seedSampleData() {
        //TODO finish this
        return null;
    }

    @PostMapping("v1/function/backup")
    public Map<String, String> backupJsonToFile() {
        List<CustomField> customFields = customFieldRepository.getAllCustomFields();
        List<ToyRequestDto> toys = toyGateway.getWithFilters(new ArrayList<>()).stream().map(ToyRequestDto::convertResponseToRequest).toList();
        List<SystemRequestDto> systems = systemGateway.getWithFilters(new ArrayList<>()).stream().map(SystemRequestDto::convertRequestToResponse).toList();

        FormattedBackupData backupData = new FormattedBackupData("backupData", customFields, toys, systems);
        ObjectMapper objectMapper = new ObjectMapper();

        File file = new File(backupDataPath);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, backupData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final FormattedResponseBody<String> body = new FormattedResponseBody<>("JSON Backup Successful, File saved to: " + file.getAbsolutePath());
        return body.formatData();
    }

    @PostMapping("v1/function/import")
    public Map<String, String> importJsonFromFile() {
        byte[] fileData;
        try {
            fileData = Files.readAllBytes(Paths.get(backupDataPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        FormattedBackupData backupData;
        try {
            backupData = objectMapper.readValue(fileData, FormattedBackupData.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //TODO refactor this to work with the sample data

        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        List<CustomField> customFields = backupData.customFields();
        Map<String, Integer> customFieldIds = new HashMap<>(customFields.size());
        for (CustomField customField : customFields) {
            try {
                CustomField savedCustomField = customFieldRepository.insertCustomField(new CustomFieldRequestDto(customField.name(), customField.type(), customField.entityKey()));
                customFieldIds.put(customFieldComboKey(savedCustomField), savedCustomField.id());
            } catch (Exception exception) {
                exceptionBackupImport.addException(exception);
            }
        }
        if (exceptionBackupImport.getExceptions().size() > 0) {
            //TODO include a message saying that there were errors with the custom fields and that the import process was stopped.
            //TODO this introduces a new problem some of the custom fields are present in the system this first step with the custom fields should really be a get then if not found then insert
            throw exceptionBackupImport;
        }

        exceptionBackupImport = importToys(backupData, customFieldIds, exceptionBackupImport);

        exceptionBackupImport = importSystems(backupData, customFieldIds, exceptionBackupImport);

        if (exceptionBackupImport.getExceptions().size() > 0) {
            throw exceptionBackupImport;
        }
        final FormattedResponseBody<String> body = new FormattedResponseBody<>("JSON Import Successful, Data pulled from: " + backupDataPath);
        return body.formatData();
    }

    private String customFieldComboKey(CustomField customField) {
        return customField.entityKey() + "-" + customField.name();
    }

    private String customFieldComboKey(String entityKey, CustomFieldValue value) {
        return entityKey + "-" + value.getCustomFieldName();
    }

    private ExceptionBackupImport importToys(FormattedBackupData backupData, Map<String, Integer> customFieldIds, ExceptionBackupImport exceptionBackupImport) {
        List<ToyRequestDto> toyRequestsToBeUpdated = backupData.toys();
        List<ToyRequestDto> toyRequestsReady = new ArrayList<>(toyRequestsToBeUpdated.size());
        for (ToyRequestDto toyRequestDto: toyRequestsToBeUpdated) {
            boolean skipped = false;
            for (CustomFieldValue value: toyRequestDto.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(customFieldComboKey(Keychain.TOY_KEY, value));
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addException(new Exception("Error restoring toy data from a file CustomFieldId not found but expected for "
                            + toyRequestDto.name() + " with custom field value " + value.getCustomFieldName() + " this toy will be skipped."));
                } else {
                    value.setCustomFieldId(customFieldId);
                }
            }
            if (!skipped) {
                toyRequestsReady.add(toyRequestDto);
            }
        }
        for (ToyRequestDto toyRequestDto: toyRequestsReady) {
            try {
                toyGateway.createNew(toyRequestDto);
            } catch (Exception exception) {
                exceptionBackupImport.addException(exception);
            }
        }
        return exceptionBackupImport;
    }

    private ExceptionBackupImport importSystems(FormattedBackupData backupData, Map<String, Integer> customFieldIds, ExceptionBackupImport exceptionBackupImport) {
        List<SystemRequestDto> systemRequestToBeUpdated = backupData.systems();
        List<SystemRequestDto> systemRequestsReady = new ArrayList<>(systemRequestToBeUpdated.size());
        for (SystemRequestDto systemRequestDto: systemRequestToBeUpdated) {
            boolean skipped = false;
            for (CustomFieldValue value: systemRequestDto.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(customFieldComboKey(Keychain.SYSTEM_KEY, value));
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addException(new Exception("Error restoring system data from a file CustomFieldId not found but expected for "
                            + systemRequestDto.name() + " with custom field value " + value.getCustomFieldName() + " this system will be skipped."));
                } else {
                    value.setCustomFieldId(customFieldId);
                }
            }
            if (!skipped) {
                systemRequestsReady.add(systemRequestDto);
            }
        }
        for (SystemRequestDto systemRequestDto: systemRequestsReady) {
            try {
                systemGateway.createNew(systemRequestDto);
            } catch (Exception exception) {
                exceptionBackupImport.addException(exception);
            }
        }
        return exceptionBackupImport;
    }

}

record FormattedBackupData(String dataType, List<CustomField> customFields, List<ToyRequestDto> toys, List<SystemRequestDto> systems) { }
