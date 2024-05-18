package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;

import java.util.LinkedHashMap;
import java.util.Map;

public class FilterEntity {

    public static Map<String, String> getFieldsForResource(String resource) {
        //Using a LinkedHashMap to preserve the order of the elements as they are added to the Map.
        Map<String, String> fields = new LinkedHashMap<>();
        switch (resource) {
            case Keychain.SYSTEM_KEY -> {
                fields.put("name", Filter.FIELD_TYPE_STRING);
                fields.put("generation", Filter.FIELD_TYPE_NUMBER);
                fields.put("handheld", Filter.FIELD_TYPE_BOOLEAN);
            }
            case Keychain.TOY_KEY -> {
                fields.put("name", Filter.FIELD_TYPE_STRING);
                fields.put("set", Filter.FIELD_TYPE_STRING);
            }
            default -> {
                return new LinkedHashMap<>();
            }
        }
        fields.put("created_at", Filter.FIELD_TYPE_TIME);
        fields.put("updated_at", Filter.FIELD_TYPE_TIME);
        fields.put(Filter.ALL_FIELDS, Filter.FIELD_TYPE_SORT);
        fields.put(Filter.PAGINATION_FIELDS, Filter.FIELD_TYPE_PAGINATION);
        return fields;
    }
}
