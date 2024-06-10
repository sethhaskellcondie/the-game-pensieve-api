package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;

import java.util.List;

public record CustomField(int id, String name, String type, String entityKey) {

    //Custom Field Types MUST always also be a Filter Field Type otherwise custom fields wouldn't work as filters
    public static final String TYPE_TEXT = Filter.FIELD_TYPE_TEXT;
    public static final String TYPE_NUMBER = Filter.FIELD_TYPE_NUMBER;
    public static final String TYPE_BOOLEAN = Filter.FIELD_TYPE_BOOLEAN;

    public static List<String> getAllCustomFieldTypes() {
        return List.of(TYPE_TEXT, TYPE_NUMBER, TYPE_BOOLEAN);
    }
}

record CustomFieldRequestDto(String name, String type, String entityKey) { }
