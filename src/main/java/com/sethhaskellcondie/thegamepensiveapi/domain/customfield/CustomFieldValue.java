package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

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
    private int customFieldId;
    private String name;
    private final String type;
    private final Object value;

    public CustomFieldValue(int customFieldId, String name, String type, Object value) {
        this.customFieldId = customFieldId;
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public int getCustomFieldId() {
        return customFieldId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }
}

record CustomFieldRequest(String name, String type, String entityKey) { }

record CustomField(int id, String name, String type, String entityKey) {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_BOOLEAN = "boolean";

    public static List<String> getAllCustomFieldTypes() {
        return List.of(TYPE_TEXT, TYPE_NUMBER, TYPE_BOOLEAN);
    }
}
