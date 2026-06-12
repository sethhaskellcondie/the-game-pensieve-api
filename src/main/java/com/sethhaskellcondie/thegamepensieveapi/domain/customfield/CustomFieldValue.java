package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CustomFieldValues are NOT entities they BELONG to entities
 * There are two additional fields saved into the database the
 * entity_id, and the entity_key they come from the Entity that
 * this CustomFieldValue belongs to.
 * The customFieldId, name, and type all come from the CustomField
 * that this value relates to.
 * <p>
 * For enum type custom fields (dropdown, radio button, progress bar) the selected option is stored
 * as a reference (valueOptionId) to a custom_field_options row. On reads the value field carries the
 * option's text (its name) and valueOptionId carries the option's id; on writes the client provides
 * the valueOptionId and the value text is derived from it. For all other types valueOptionId is null.
 */
public class CustomFieldValue {
    private int customFieldId;
    private final String customFieldName;
    private final String customFieldType;
    private final String value;
    private Integer valueOptionId;

    //The JsonProperty annotations are here to help Jackson serialize objects
    public CustomFieldValue(
            @JsonProperty("customFieldId") int customFieldId,
            @JsonProperty("customFieldName") String customFieldName,
            @JsonProperty("customFieldType") String customFieldType,
            @JsonProperty("value") String value,
            @JsonProperty("valueOptionId") Integer valueOptionId
    ) {
        this.customFieldId = customFieldId;
        this.customFieldName = customFieldName;
        this.customFieldType = customFieldType;
        this.value = value;
        this.valueOptionId = valueOptionId;
    }

    //Convenience constructor for non-enum values (text, number, boolean) which never reference an option.
    public CustomFieldValue(int customFieldId, String customFieldName, String customFieldType, String value) {
        this(customFieldId, customFieldName, customFieldType, value, null);
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

    public Integer getValueOptionId() {
        return valueOptionId;
    }

    //should only be called during backup import to remap the option reference to the newly inserted option id
    public void setValueOptionId(Integer valueOptionId) {
        this.valueOptionId = valueOptionId;
    }
}
