package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import java.util.List;

public record CustomField(int id, String name, String type, String entityKey) {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_BOOLEAN = "boolean";

    public static List<String> getAllCustomFieldTypes() {
        return List.of(TYPE_TEXT, TYPE_NUMBER, TYPE_BOOLEAN);
    }
}

record CustomFieldRequestDto(String name, String type, String entityKey) { }
