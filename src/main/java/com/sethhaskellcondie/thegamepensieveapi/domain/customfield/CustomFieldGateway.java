package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomFieldGateway {
    private final CustomFieldRepository repository;

    public CustomFieldGateway(CustomFieldRepository repository) {
        this.repository = repository;
    }

    public CustomField createNew(CustomFieldRequestDto customField) {
        return repository.insertCustomField(customField);
    }

    public List<CustomField> getAllCustomFields() {
        return repository.getAllCustomFields();
    }

    public CustomField getById(int id) {
        return repository.getById(id);
    }

    public CustomField updateName(int id, String newName) {
        return repository.updateName(id, newName);
    }

    public void deleteById(int id) {
        repository.deleteById(id);
    }

    public List<CustomField> getAllByEntityKey(String entityKey) {
        if (!Keychain.getAllKeys().contains(entityKey)) {
            throw new ExceptionResourceNotFound("Invalid entity key: " + entityKey 
                    + ". Valid keys are: [" + String.join(", ", Keychain.getAllKeys()) + "]");
        }
        return repository.getAllByKey(entityKey);
    }
}
