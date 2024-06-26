package com.sethhaskellcondie.thegamepensiveapi.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyRequestDto;
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
        List<CustomField> customFields = customFieldRepository.getAllCustomFields();
        List<ToyRequestDto> toys = toyGateway.getWithFilters(new ArrayList<>()).stream().map(ToyRequestDto::convertResponseToRequest).toList();
        List<SystemRequestDto> systems = systemGateway.getWithFilters(new ArrayList<>()).stream().map(SystemRequestDto::convertRequestToResponse).toList();

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
        //save custom fields
        //save toys
        //save systems
        return null;
    }
}

record FormattedBackupData(String dataType, List<CustomField> customFields, List<ToyRequestDto> toys, List<SystemRequestDto> systems) { }
