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
    private final boolean deleted;

    public CustomFieldValue(int customFieldId, String customFieldName, String customFieldType, String value, boolean deleted) {
        this.customFieldId = customFieldId;
        this.customFieldName = customFieldName;
        this.customFieldType = customFieldType;
        this.value = value;
        this.deleted = deleted;
    }

    public int getCustomFieldId() {
        return customFieldId;
    }

    //should only be called in the CustomFieldValueRepository to update the customFieldValue id after a new one is created
    public void setCustomFieldId(int customFieldId) {
        this.customFieldId = customFieldId;
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

    public boolean isDeleted() {
        return deleted;
    }

    public CustomFieldValueDao convertToDao(int entityId, String entityKey) {
        switch (this.getCustomFieldType()) {
            case CustomField.TYPE_TEXT -> {
                return new CustomFieldValueDao(this.customFieldId, entityId, entityKey, this.value, null, this.deleted);
            }
            case CustomField.TYPE_NUMBER -> {
                try {
                    return new CustomFieldValueDao(this.customFieldId, entityId, entityKey, null, Integer.parseInt(this.value), this.deleted);
                } catch (NumberFormatException exception) {
                    throw new ExceptionMalformedEntity(List.of(new Exception("Malformed Custom Field Value: if the Custom Field Type is number the value must be a valid Integer.")));
                }
            }
            case CustomField.TYPE_BOOLEAN -> {
                if (Objects.equals(this.value, "true") || Objects.equals(this.value, "false")) {
                    return new CustomFieldValueDao(this.customFieldId, entityId, entityKey, this.value, null, this.deleted);
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
