package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;

import java.util.List;

//Custom Fields are NOT entities, they don't follow the entity pattern
//Custom Field is a pseudo-DTO this is why it is allowed to be public and used in the api layer
public record CustomField(int id, String name, String type, String entityKey, List<CustomFieldOption> options) {

    //Custom Field Types MUST always also be a Filter Field Type otherwise custom fields wouldn't work as filters
    public static final String TYPE_TEXT         = Filter.FIELD_TYPE_TEXT;
    public static final String TYPE_NUMBER       = Filter.FIELD_TYPE_NUMBER;
    public static final String TYPE_BOOLEAN      = Filter.FIELD_TYPE_BOOLEAN;
    public static final String TYPE_DROPDOWN     = Filter.FIELD_TYPE_DROPDOWN;
    public static final String TYPE_RADIO_BUTTON = Filter.FIELD_TYPE_RADIO_BUTTON;
    public static final String TYPE_PROGRESS_BAR = Filter.FIELD_TYPE_PROGRESS_BAR;

    public static CustomField withoutOptions(int id, String name, String type, String entityKey) {
        return new CustomField(id, name, type, entityKey, List.of());
    }

    public static List<String> getAllCustomFieldTypes() {
        return List.of(TYPE_TEXT, TYPE_NUMBER, TYPE_BOOLEAN,
                TYPE_DROPDOWN, TYPE_RADIO_BUTTON, TYPE_PROGRESS_BAR);
    }

    public static List<String> getEnumCustomFieldTypes() {
        return List.of(TYPE_DROPDOWN, TYPE_RADIO_BUTTON, TYPE_PROGRESS_BAR);
    }

    public static boolean isEnumType(String type) {
        return getEnumCustomFieldTypes().contains(type);
    }
}
