package com.sethhaskellcondie.thegamepensiveapi.domain.backupimport;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.Toy;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionBackupImport;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BackupImportService {
    private final SystemRepository systemRepository;
    private final ToyRepository toyRepository;
    private final CustomFieldRepository customFieldRepository;

    protected BackupImportService(SystemRepository systemRepository, ToyRepository toyRepository, CustomFieldRepository customFieldRepository) {
        this.systemRepository = systemRepository;
        this.toyRepository = toyRepository;
        this.customFieldRepository = customFieldRepository;
    }

    protected BackupDataDto getBackupData() {
        List<CustomField> customFields = customFieldRepository.getAllCustomFields();
        List<ToyRequestDto> toys = toyRepository.getWithFilters(new ArrayList<>()).stream().map(Toy::convertToRequestDto).toList();
        List<SystemRequestDto> systems = systemRepository.getWithFilters(new ArrayList<>()).stream().map(System::convertToRequestDto).toList();

        return new BackupDataDto(customFields, toys, systems);
    }

    protected ImportResultsDto importBackupData(BackupDataDto backupDataDto) {
        final Map<String, Integer> customFieldIds;
        final ImportCustomFieldsResults customFieldResults;
        customFieldResults = importCustomFields(backupDataDto);
        if (customFieldResults.exceptionBackupImport().getExceptions().size() > 0) {
            ExceptionBackupImport customFieldsException = new ExceptionBackupImport("There were errors importing Custom Fields. No additional data imported.");
            customFieldsException.appendExceptions(customFieldResults.exceptionBackupImport().getExceptions());
            return new ImportResultsDto(customFieldResults.existingCount(), customFieldResults.createdCount(), customFieldsException);
        }

        customFieldIds = customFieldResults.customFieldIds();
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport("There were errors importing Entity data, all valid data was imported, data with errors was skipped.");

        ImportEntityResults toyResults = importToys(backupDataDto, customFieldIds);
        if (toyResults.exceptionBackupImport().getExceptions().size() > 0) {
            exceptionBackupImport.appendExceptions(toyResults.exceptionBackupImport().getExceptions());
        }

        ImportEntityResults systemResults = importSystems(backupDataDto, customFieldIds);
        if (systemResults.exceptionBackupImport().getExceptions().size() > 0) {
            exceptionBackupImport.appendExceptions(systemResults.exceptionBackupImport().getExceptions());
        }

        return new ImportResultsDto(
                customFieldResults.existingCount(), customFieldResults.createdCount(),
                toyResults.existingCount(), toyResults.createdCount(),
                systemResults.existingCount(), systemResults.createdCount(),
                exceptionBackupImport
        );
    }

    private ImportCustomFieldsResults importCustomFields(BackupDataDto backupDataDto) {
        final ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        int existingCount = 0;
        int createdCount = 0;

        final List<CustomField> customFields = backupDataDto.customFields();
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

    private ImportEntityResults importToys(BackupDataDto backupDataDto, Map<String, Integer> customFieldIds) {
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        int existingCount = 0;
        int createdCount = 0;

        List<ToyRequestDto> toyRequestsToBeUpdated = backupDataDto.toys();
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
                int toyId = toyRepository.getIdByNameAndSet(toyRequestDto.name(), toyRequestDto.set());
                if (toyId > 0) {
                    Toy toy = toyRepository.getById(toyId);
                    toy.updateFromRequestDto(toyRequestDto);
                    toyRepository.update(toy);
                    existingCount++;
                } else {
                    toyRepository.insert(toyRequestDto);
                    createdCount++;
                }
            } catch (Exception exception) {
                exceptionBackupImport.addException(new Exception("Error importing toy data with name: '" + toyRequestDto.name()
                        + "' and set '" + toyRequestDto.set() + "'" + exception.getMessage()));
            }
        }
        return new ImportEntityResults(existingCount, createdCount, exceptionBackupImport);
    }

    private ImportEntityResults importSystems(BackupDataDto backupDataDto, Map<String, Integer> customFieldIds) {
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        int existingCount = 0;
        int createdCount = 0;

        List<SystemRequestDto> systemRequestToBeUpdated = backupDataDto.systems();
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
                systemRepository.insert(systemRequestDto);
                createdCount++;
            } catch (Exception exception) {
                exceptionBackupImport.addException(exception);
            }
        }
        return new ImportEntityResults(existingCount, createdCount, exceptionBackupImport);
    }
}

record ImportCustomFieldsResults(Map<String, Integer> customFieldIds, int existingCount, int createdCount, ExceptionBackupImport exceptionBackupImport) { }
record ImportEntityResults(int existingCount, int createdCount, ExceptionBackupImport exceptionBackupImport) { }

