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
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
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
import java.util.Objects;

/**
 * This class acts like another system that is trying to interact with the domain. It can only use the DTO objects (not the entities)
 * and only has access to the gateways
 */
@RestController
public class BackupImportController {

    private final SystemGateway systemGateway;
    private final ToyGateway toyGateway;
    //TODO refactor the CustomFields to have a Gateway and use that instead of the Repository
    private final CustomFieldRepository customFieldRepository;
    private final String backupDataPath = "backup.json";

    public BackupImportController(SystemGateway systemGateway, ToyGateway toyGateway, CustomFieldRepository customFieldRepository) {
        this.systemGateway = systemGateway;
        this.toyGateway = toyGateway;
        this.customFieldRepository = customFieldRepository;
    }

    @PostMapping("v1/function/backup")
    public Map<String, FormattedBackupData> backupJsonToFile() {
        List<CustomField> customFields = customFieldRepository.getAllCustomFields();
        List<ToyRequestDto> toys = toyGateway.getWithFilters(new ArrayList<>()).stream().map(ToyRequestDto::convertResponseToRequest).toList();
        List<SystemRequestDto> systems = systemGateway.getWithFilters(new ArrayList<>()).stream().map(SystemRequestDto::convertRequestToResponse).toList();

        File file = new File(backupDataPath);
        ObjectMapper objectMapper = new ObjectMapper();
        FormattedBackupData backupData = new FormattedBackupData(file.getAbsolutePath(), customFields, toys, systems);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, backupData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final FormattedResponseBody<FormattedBackupData> body = new FormattedResponseBody<>(backupData);
        return body.formatData();
    }

    @PostMapping("v1/function/import")
    public ImportResultsResponse importJsonFromFile() {
        final FormattedBackupData backupData;
        try {
            final byte[] fileData = Files.readAllBytes(Paths.get(backupDataPath));
            final ObjectMapper objectMapper = new ObjectMapper();
            backupData = objectMapper.readValue(fileData, FormattedBackupData.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ImportResults importResults = importBackupData(backupData);
        return new ImportResultsResponse(importResults.data(), importResults.exceptionBackupImport().getMessages());
    }

    @PostMapping("v1/function/seedSampleData")
    public ImportResultsResponse seedSampleData() {
        final FormattedBackupData sampleData;
        try {
            final byte[] fileData = Files.readAllBytes(Paths.get("sampleData.json"));
            final ObjectMapper objectMapper = new ObjectMapper();
            sampleData = objectMapper.readValue(fileData, FormattedBackupData.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ImportResults importResults = importBackupData(sampleData);
        return new ImportResultsResponse(importResults.data(), importResults.exceptionBackupImport().getMessages());
    }

    private ImportResults importBackupData(FormattedBackupData backupData) {
        final Map<String, Integer> customFieldIds;
        final ImportCustomFieldResults customFieldResults;
        try {
            customFieldResults = importCustomFields(backupData);
            customFieldIds = customFieldResults.customFieldIds();
        } catch (ExceptionBackupImport customFieldException) {
            ExceptionBackupImport importException = new ExceptionBackupImport("There were errors importing Custom Fields, address the provided errors and try again. No additional data imported.");
            importException.appendExceptions(customFieldException.getExceptions());
            throw importException;
        }

        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport("There were errors importing Entity data, all valid data was imported, data with errors was skipped.");

        try {
            importToys(backupData, customFieldIds);
        } catch (ExceptionBackupImport toyException) {
            exceptionBackupImport.appendExceptions(toyException.getExceptions());
        }

        try {
            importSystems(backupData, customFieldIds);
        } catch (ExceptionBackupImport systemException) {
            exceptionBackupImport.appendExceptions(systemException.getExceptions());
        }

        ImportResultsData data = new ImportResultsData(customFieldResults.existingCount(), customFieldResults.createdCount());
        return new ImportResults(data, exceptionBackupImport);
    }

    private ImportCustomFieldResults importCustomFields(FormattedBackupData backupData) {
        final ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        int existingCount = 0;
        int createdCount = 0;
        final List<CustomField> customFields = backupData.customFields();
        final Map<String, Integer> customFieldIds = new HashMap<>(customFields.size());

        for (CustomField customField : customFields) {
            CustomField savedCustomField;
            try {
                savedCustomField = customFieldRepository.getByKeyAndName(customField.entityKey(), customField.name());
                if (!Objects.equals(savedCustomField.type(), customField.type())) {
                    exceptionBackupImport.addException(new Exception("Error Importing Custom Field Data: Provided custom field with name: '"
                            + customField.name() + "' and key: '" + customField.entityKey() + "' had a type mismatch with the existing custom field in the database provided type: '"
                            + customField.type() + "' existing (correct) type: '" + savedCustomField.type() + "'"));
                } else {
                    existingCount++;
                }
            } catch (ExceptionResourceNotFound ignored) {
                savedCustomField = null;
            }
            if (null != savedCustomField) {
                try {
                    savedCustomField = customFieldRepository.insertCustomField(new CustomFieldRequestDto(customField.name(), customField.type(), customField.entityKey()));
                    createdCount++;
                } catch (Exception exception) {
                    exceptionBackupImport.addException(new Exception("Error Importing Custom Field Data: Provided custom field with name: '"
                            + customField.name() + "' Message: " + exception.getMessage()));
                }
            }
            if (null != savedCustomField) {
                customFieldIds.put(customFieldComboKey(savedCustomField), savedCustomField.id());
            }
        }
        if (exceptionBackupImport.getExceptions().size() > 0) {
            throw exceptionBackupImport;
        }
        return new ImportCustomFieldResults(customFieldIds, existingCount, createdCount);
    }

    private String customFieldComboKey(CustomField customField) {
        return customField.entityKey() + "-" + customField.name();
    }

    private String customFieldComboKey(String entityKey, CustomFieldValue value) {
        return entityKey + "-" + value.getCustomFieldName();
    }

    private ExceptionBackupImport importToys(FormattedBackupData backupData, Map<String, Integer> customFieldIds) {
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
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
        if (exceptionBackupImport.getExceptions().size() > 0) {
            throw exceptionBackupImport;
        }
        return exceptionBackupImport;
    }

    private ExceptionBackupImport importSystems(FormattedBackupData backupData, Map<String, Integer> customFieldIds) {
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
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
        if (exceptionBackupImport.getExceptions().size() > 0) {
            throw exceptionBackupImport;
        }
        return exceptionBackupImport;
    }

}

record FormattedBackupData(String filePath, List<CustomField> customFields, List<ToyRequestDto> toys, List<SystemRequestDto> systems) { }
record ImportCustomFieldResults(Map<String, Integer> customFieldIds, int existingCount, int createdCount) { }
record ImportResultsData(int preexistingCustomFields, int createdCustomFields) { }
record ImportResults(ImportResultsData data, ExceptionBackupImport exceptionBackupImport) { }
record ImportResultsResponse(ImportResultsData data, List<String> errors) { }
