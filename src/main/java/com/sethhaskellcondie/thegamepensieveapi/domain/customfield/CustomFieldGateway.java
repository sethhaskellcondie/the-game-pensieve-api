package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CustomFieldGateway {
    private final CustomFieldRepository repository;
    private final CustomFieldOptionRepository optionRepository;

    public CustomFieldGateway(CustomFieldRepository repository, CustomFieldOptionRepository optionRepository) {
        this.repository = repository;
        this.optionRepository = optionRepository;
    }

    @Transactional
    public CustomField createNew(CustomFieldRequestDto customField) {
        if (CustomField.isEnumType(customField.type())) {
            if (customField.options() == null || customField.options().isEmpty()) {
                throw new ExceptionFailedDbValidation("Enum type custom fields require at least one option. "
                        + "Include an 'options' list with at least one option name in the request.");
            }
        }
        CustomField saved = repository.insertCustomField(customField);
        if (customField.options() != null) {
            for (int i = 0; i < customField.options().size(); i++) {
                optionRepository.insertOption(saved.id(), customField.options().get(i), i == 0);
            }
        }
        return repository.getById(saved.id());
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

    public CustomField addOption(int customFieldId, String name) {
        CustomField customField = repository.getById(customFieldId);
        if (!CustomField.isEnumType(customField.type())) {
            throw new ExceptionFailedDbValidation("Cannot add options to a custom field of type '" + customField.type()
                    + "'. Options are only supported for enum types: " + CustomField.getEnumCustomFieldTypes() + ".");
        }
        List<CustomFieldOption> existingOptions = optionRepository.getOptionsByCustomFieldId(customFieldId);
        boolean isFirstOption = existingOptions.isEmpty();
        optionRepository.insertOption(customFieldId, name, isFirstOption);
        return repository.getById(customFieldId);
    }

    public CustomField updateOptionName(int customFieldId, int optionId, String newName) {
        validateOptionOwnership(customFieldId, optionId);
        optionRepository.updateOptionName(optionId, newName);
        return repository.getById(customFieldId);
    }

    public CustomField setDefaultOption(int customFieldId, int optionId) {
        validateOptionOwnership(customFieldId, optionId);
        optionRepository.setDefaultOption(customFieldId, optionId);
        return repository.getById(customFieldId);
    }

    public CustomField deleteOption(int customFieldId, int optionId) {
        CustomFieldOption option = validateOptionOwnership(customFieldId, optionId);
        if (option.isDefault()) {
            throw new ExceptionFailedDbValidation("Cannot delete the default option (id: " + optionId
                    + "). Set a different option as the default first.");
        }
        optionRepository.deleteOption(optionId, customFieldId);
        return repository.getById(customFieldId);
    }

    private CustomFieldOption validateOptionOwnership(int customFieldId, int optionId) {
        CustomFieldOption option = optionRepository.getOptionById(optionId);
        if (option.customFieldId() != customFieldId) {
            throw new ExceptionResourceNotFound("Option id " + optionId + " does not belong to custom field id " + customFieldId + ".");
        }
        return option;
    }
}
