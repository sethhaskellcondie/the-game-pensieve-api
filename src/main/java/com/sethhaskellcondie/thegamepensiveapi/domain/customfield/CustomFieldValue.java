package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

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

record CustomField(int id, String name, String type, String entityKey) { }
