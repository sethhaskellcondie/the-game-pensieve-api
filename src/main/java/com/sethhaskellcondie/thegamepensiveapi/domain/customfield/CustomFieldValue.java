package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;

import java.util.List;
import java.util.Objects;

/**
 * CustomFieldValues belong to Entities
 * There are two additional fields saved into the database the
 * entity_id, and the entity_key they come from the Entity that
 * this CustomFieldValue belongs to.
 * The customFieldId, name, and type all come from the CustomField
 * that this value relates to.
 */
public class CustomFieldValue {
    private int customFieldId;
    private String customFieldName;
    private final String customFieldType;
    private final String value;

    public CustomFieldValue(int customFieldId, String customFieldName, String customFieldType, String value) {
        this.customFieldId = customFieldId;
        this.customFieldName = customFieldName;
        this.customFieldType = customFieldType;
        this.value = value;
    }

    public int getCustomFieldId() {
        return customFieldId;
    }

    public String getCustomFieldName() {
        return customFieldName;
    }

    public String getCustomFieldType() {
        return customFieldType;
    }

    public String getValue() {
        return value;
    }

    public CustomFieldValueDao convertToDao(int entityId, String entityKey) {
        switch (this.getCustomFieldType()) {
            case CustomField.TYPE_TEXT -> {
                return new CustomFieldValueDao(this.customFieldId, entityId, entityKey, this.value, null);
            }
            case CustomField.TYPE_NUMBER -> {
                try {
                    return new CustomFieldValueDao(this.customFieldId, entityId, entityKey, null, Integer.parseInt(this.value));
                } catch (NumberFormatException exception) {
                    throw new ExceptionMalformedEntity(List.of(new Exception("Malformed Custom Field Value: if the Custom Field Type is number the value must be a valid Integer.")));
                }
            }
            case CustomField.TYPE_BOOLEAN -> {
                if (Objects.equals(this.value, "true") || Objects.equals(this.value, "false")) {
                    return new CustomFieldValueDao(this.customFieldId, entityId, entityKey, this.value, null);
                }
                throw new ExceptionMalformedEntity(List.of(new Exception("Malformed Custom Field Value: if the Custom Field Type is boolean the value must be exactly 'true' or 'false'.")));
            }
            default -> {
                throw new ExceptionMalformedEntity(List.of(new Exception("Malformed Custom Field Value: unknown Custom Field Type provided: " + this.getCustomFieldType() +
                        ". Valid types include [" + String.join(", ", CustomField.getAllCustomFieldTypes()) + "]")));
            }
        }
    }
}

record CustomFieldRequestDto(String name, String type, String entityKey) { }

record CustomField(int id, String name, String type, String entityKey) {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_BOOLEAN = "boolean";

    public static List<String> getAllCustomFieldTypes() {
        return List.of(TYPE_TEXT, TYPE_NUMBER, TYPE_BOOLEAN);
    }
}

record CustomFieldValueDao(int customFieldsId, int entityId, String entityKey, String valueText, Integer valueNumber) {
    public CustomFieldValue convertToValue(String customFieldName, String customFieldType) {
        if (Objects.equals(customFieldType, CustomField.TYPE_TEXT) || Objects.equals(customFieldType, CustomField.TYPE_BOOLEAN)) {
            return new CustomFieldValue(this.customFieldsId, customFieldName, customFieldType, this.valueText);
        }
        return new CustomFieldValue(this.customFieldsId, customFieldName, customFieldType, this.valueNumber.toString());
    }
}
