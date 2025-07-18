package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;

import java.util.List;

//Custom Fields are NOT entities, they don't follow the entity pattern
//Custom Field is a pseudo-DTO this is why it is allowed to be public and used in the api layer
public record CustomField(int id, String name, String type, String entityKey) {

    //Custom Field Types MUST always also be a Filter Field Type otherwise custom fields wouldn't work as filters
    public static final String TYPE_TEXT = Filter.FIELD_TYPE_TEXT;
    public static final String TYPE_NUMBER = Filter.FIELD_TYPE_NUMBER;
    public static final String TYPE_BOOLEAN = Filter.FIELD_TYPE_BOOLEAN;

    public static List<String> getAllCustomFieldTypes() {
        return List.of(TYPE_TEXT, TYPE_NUMBER, TYPE_BOOLEAN);
    }
}
