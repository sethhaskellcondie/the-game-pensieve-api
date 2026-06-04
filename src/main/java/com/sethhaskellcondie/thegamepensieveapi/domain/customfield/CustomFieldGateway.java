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

    @Transactional
    public CustomField update(int id, CustomFieldUpdateRequestDto dto) {
        if (dto.options() != null) {
            CustomField customField = repository.getById(id);
            if (!CustomField.isEnumType(customField.type())) {
                throw new ExceptionFailedDbValidation("Cannot manage options on a custom field of type '" + customField.type()
                        + "'. Options are only supported for enum types: " + CustomField.getEnumCustomFieldTypes() + ".");
            }
            if (dto.options().isEmpty()) {
                throw new ExceptionFailedDbValidation("Enum type custom fields require at least one option.");
            }
            long defaultCount = dto.options().stream().filter(CustomFieldOptionDto::isDefault).count();
            if (defaultCount != 1) {
                throw new ExceptionFailedDbValidation("Exactly one option must be marked as the default. Found: " + defaultCount + ".");
            }
            List<CustomFieldOption> currentOptions = optionRepository.getOptionsByCustomFieldId(id);
            List<Integer> incomingIds = dto.options().stream()
                    .filter(o -> o.id() != null)
                    .map(CustomFieldOptionDto::id)
                    .toList();
            for (CustomFieldOptionDto optionDto : dto.options()) {
                if (optionDto.id() != null) {
                    optionRepository.updateOption(optionDto.id(), optionDto.name(), optionDto.order(), optionDto.isDefault());
                } else {
                    optionRepository.insertOption(id, optionDto.name(), optionDto.isDefault());
                }
            }
            for (CustomFieldOption current : currentOptions) {
                if (!incomingIds.contains(current.id())) {
                    optionRepository.deleteOption(current.id(), id);
                }
            }
        }
        return repository.update(id, dto.name(), dto.order());
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
