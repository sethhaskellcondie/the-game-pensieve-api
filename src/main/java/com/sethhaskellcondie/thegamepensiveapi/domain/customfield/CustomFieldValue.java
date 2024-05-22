package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * CustomFieldValues belong to Entities
 * There are two additional fields saved into the database the
 * entity_id, and the entity_key they come from the Entity that
 * this CustomFieldValue belongs to.
 * The customFieldId, name, and type all come from the CustomField
 * that this value relates to.
 */
public class CustomFieldValue {
    //TODO find a way to allow the customFieldId to be null? Or let 0 be ok?
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
