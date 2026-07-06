package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AccessService;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Capability;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionForbidden;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CustomFieldGateway {
    private final CustomFieldRepository repository;
    private final CustomFieldOptionRepository optionRepository;
    private final AccessService access;

    public CustomFieldGateway(CustomFieldRepository repository, CustomFieldOptionRepository optionRepository, AccessService access) {
        this.repository = repository;
        this.optionRepository = optionRepository;
        this.access = access;
    }

    @Transactional
    public CustomField createNew(CustomFieldRequestDto customField) {
        requireWrite();
        if (CustomField.isEnumType(customField.type())) {
            if (customField.options() == null || customField.options().isEmpty()) {
                throw new ExceptionFailedDbValidation("Enum type custom fields require at least one option. "
                        + "Include an 'options' list with at least one option name in the request.");
            }
            long defaultCount = customField.options().stream().filter(CustomFieldOptionDto::isDefault).count();
            if (defaultCount != 1) {
                throw new ExceptionFailedDbValidation("Exactly one option must be marked as the default. Found: " + defaultCount + ".");
            }
        }
        CustomField saved = repository.insertCustomField(customField);
        if (customField.options() != null) {
            for (CustomFieldOptionDto option : customField.options()) {
                optionRepository.insertOption(saved.id(), option.name(), option.isDefault(), option.order());
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
        requireWrite();
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
                    optionRepository.insertOption(id, optionDto.name(), optionDto.isDefault(), optionDto.order());
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
        requireWrite();
        repository.deleteById(id);
    }

    public List<CustomField> getAllByEntityKey(String entityKey) {
        if (!Keychain.getAllKeys().contains(entityKey)) {
            throw new ExceptionResourceNotFound("Invalid entity key: " + entityKey
                    + ". Valid keys are: [" + String.join(", ", Keychain.getAllKeys()) + "]");
        }
        return repository.getAllByKey(entityKey);
    }

    // Custom fields are read-only on the public showcase read surface: reads (getAllByEntityKey / getAllCustomFields
    // / getById) are ungated so an anonymous or X-Showcase (GUEST) viewer can browse an owner's definitions, but
    // mutations require the WRITE capability. A GUEST showcase view lacks it (403); enforcement is off in the default
    // permit-all build, so the single-user public build keeps unrestricted writes. Mirrors EntityGatewayAbstract.
    private void requireWrite() {
        if (!access.can(Capability.WRITE)) {
            throw new ExceptionForbidden("An active subscription is required to create, update, or delete data.");
        }
    }

}
