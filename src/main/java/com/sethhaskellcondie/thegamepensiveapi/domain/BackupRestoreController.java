package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyGateway;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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

    @PostMapping("v1/backup")
    public Map<String, String> backupJsonToFile() {
        return null;
    }

    @PostMapping("v1/restore")
    public Map<String, String> restoreJsonFromFile() {
        return null;
    }
}
