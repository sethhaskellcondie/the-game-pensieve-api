package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CustomFieldValues are NOT entities they BELONG to entities
 * There are two additional fields saved into the database the
 * entity_id, and the entity_key they come from the Entity that
 * this CustomFieldValue belongs to.
 * The customFieldId, name, and type all come from the CustomField
 * that this value relates to.
 */
public class CustomFieldValue {
    private int customFieldId;
    private final String customFieldName;
    private final String customFieldType;
    private final String value;

    //The JsonProperty annotations are here to help Jackson serialize objects
    public CustomFieldValue(
            @JsonProperty("customFieldId") int customFieldId,
            @JsonProperty("customFieldName") String customFieldName,
            @JsonProperty("customFieldType") String customFieldType,
            @JsonProperty("value") String value
    ) {
        this.customFieldId = customFieldId;
        this.customFieldName = customFieldName;
        this.customFieldType = customFieldType;
        this.value = value;
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
}
