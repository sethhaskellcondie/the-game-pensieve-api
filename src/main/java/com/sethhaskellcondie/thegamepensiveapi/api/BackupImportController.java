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
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionBackupImport;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("v1/function/importFromFile")
    public FormattedImportResultsResponse importJsonFromFile() {
        final FormattedBackupData backupData;
        try {
            final byte[] fileData = Files.readAllBytes(Paths.get(backupDataPath));
            final ObjectMapper objectMapper = new ObjectMapper();
            backupData = objectMapper.readValue(fileData, FormattedBackupData.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ImportResults importResults = importBackupData(backupData);
        return new FormattedImportResultsResponse(importResults.data(), importResults.exceptionBackupImport().getMessages());
    }

    @PostMapping("v1/function/import")
    public FormattedImportResultsResponse importJsonFromRequestBody(@RequestBody Map<String, FormattedBackupData> requestBody) {
        final FormattedBackupData backupData = requestBody.get("data");

        ImportResults importResults = importBackupData(backupData);

        return new FormattedImportResultsResponse(importResults.data(), importResults.exceptionBackupImport().getMessages());
    }

    @PostMapping("v1/function/seedSampleData")
    public FormattedImportResultsResponse seedSampleData() {
        final FormattedBackupData sampleData;
        try {
            final byte[] fileData = Files.readAllBytes(Paths.get("sampleData.json"));
            final ObjectMapper objectMapper = new ObjectMapper();
            sampleData = objectMapper.readValue(fileData, FormattedBackupData.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ImportResults importResults = importBackupData(sampleData);
        return new FormattedImportResultsResponse(importResults.data(), importResults.exceptionBackupImport().getMessages());
    }

    /**
     * If needed everything below could be refactored into a service with a gateway that is accessed through this controller
     */

    private ImportResults importBackupData(FormattedBackupData backupData) {
        final Map<String, Integer> customFieldIds;
        final ImportCustomFieldsResults customFieldResults;
        customFieldResults = importCustomFields(backupData);
        customFieldIds = customFieldResults.customFieldIds();
        if (customFieldResults.exceptionBackupImport().getExceptions().size() > 0) {
            ExceptionBackupImport customFieldsException = new ExceptionBackupImport("There were errors importing Custom Fields. No additional data imported.");
            customFieldsException.appendExceptions(customFieldResults.exceptionBackupImport().getExceptions());
            ImportResultsData data = new ImportResultsData(customFieldResults.existingCount(), customFieldResults.createdCount());
            return new ImportResults(data, customFieldsException);
        }

        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport("There were errors importing Entity data, all valid data was imported, data with errors was skipped.");

        ImportEntityResults toyResults = importToys(backupData, customFieldIds);
        if (toyResults.exceptionBackupImport().getExceptions().size() > 0) {
            exceptionBackupImport.appendExceptions(toyResults.exceptionBackupImport().getExceptions());
        }

        ImportEntityResults systemResults = importSystems(backupData, customFieldIds);
        if (systemResults.exceptionBackupImport().getExceptions().size() > 0) {
            exceptionBackupImport.appendExceptions(systemResults.exceptionBackupImport().getExceptions());
        }

        ImportResultsData data = new ImportResultsData(
                customFieldResults.existingCount(),
                customFieldResults.createdCount(),
                toyResults.existingCount(),
                toyResults.createdCount(),
                systemResults.existingCount(),
                systemResults.createdCount()
        );
        return new ImportResults(data, exceptionBackupImport);
    }

    private ImportCustomFieldsResults importCustomFields(FormattedBackupData backupData) {
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
        return new ImportCustomFieldsResults(customFieldIds, existingCount, createdCount, exceptionBackupImport);
    }

    private String customFieldComboKey(CustomField customField) {
        return customField.entityKey() + "-" + customField.name();
    }

    private String customFieldComboKey(String entityKey, CustomFieldValue value) {
        return entityKey + "-" + value.getCustomFieldName();
    }

    private ImportEntityResults importToys(FormattedBackupData backupData, Map<String, Integer> customFieldIds) {
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        int existingCount = 0;
        int createdCount = 0;

        List<ToyRequestDto> toyRequestsToBeUpdated = backupData.toys();
        List<ToyRequestDto> toyRequestsReady = new ArrayList<>(toyRequestsToBeUpdated.size());
        for (ToyRequestDto toyRequestDto: toyRequestsToBeUpdated) {
            boolean skipped = false;
            for (CustomFieldValue value: toyRequestDto.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(customFieldComboKey(Keychain.TOY_KEY, value));
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addException(new Exception("Error importing toy data CustomFieldId not found but expected for "
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
                //TODO add existing check
                toyGateway.createNew(toyRequestDto);
                createdCount++;
            } catch (Exception exception) {
                exceptionBackupImport.addException(exception);
            }
        }
        return new ImportEntityResults(existingCount, createdCount, exceptionBackupImport);
    }

    private ImportEntityResults importSystems(FormattedBackupData backupData, Map<String, Integer> customFieldIds) {
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        int existingCount = 0;
        int createdCount = 0;

        List<SystemRequestDto> systemRequestToBeUpdated = backupData.systems();
        List<SystemRequestDto> systemRequestsReady = new ArrayList<>(systemRequestToBeUpdated.size());
        for (SystemRequestDto systemRequestDto: systemRequestToBeUpdated) {
            boolean skipped = false;
            for (CustomFieldValue value: systemRequestDto.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(customFieldComboKey(Keychain.SYSTEM_KEY, value));
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addException(new Exception("Error importing system data CustomFieldId not found but expected for "
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
                //TODO add existing check
                systemGateway.createNew(systemRequestDto);
                createdCount++;
            } catch (Exception exception) {
                exceptionBackupImport.addException(exception);
            }
        }
        return new ImportEntityResults(existingCount, createdCount, exceptionBackupImport);
    }
}

record FormattedBackupData(String filePath, List<CustomField> customFields, List<ToyRequestDto> toys, List<SystemRequestDto> systems) { }

record ImportCustomFieldsResults(Map<String, Integer> customFieldIds, int existingCount, int createdCount, ExceptionBackupImport exceptionBackupImport) { }
record ImportEntityResults(int existingCount, int createdCount, ExceptionBackupImport exceptionBackupImport) { }

record ImportResultsData(int existingCustomFields, int createdCustomFields, int existingToys, int createdToys, int existingSystems, int createdSystems) {
    ImportResultsData(int existingCustomFields, int createdCustomFields) {
        this(existingCustomFields, createdCustomFields, 0, 0, 0, 0);
    }
}
record ImportResults(ImportResultsData data, ExceptionBackupImport exceptionBackupImport) { }
record FormattedImportResultsResponse(ImportResultsData data, List<String> errors) { }
