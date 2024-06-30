package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

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
}
