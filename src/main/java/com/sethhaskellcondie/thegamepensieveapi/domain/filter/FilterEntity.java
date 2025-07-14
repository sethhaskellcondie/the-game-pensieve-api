package com.sethhaskellcondie.thegamepensieveapi.domain.filter;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FilterEntity {

    private FilterEntity() {
        // Private constructor to prevent instantiation
    }

    public static Map<String, String> getNonCustomFieldFiltersByKey(String key) {
        //Using a LinkedHashMap to preserve the order of the elements as they are added to the Map.
        Map<String, String> fields = new LinkedHashMap<>();
        switch (key) {
            case Keychain.SYSTEM_KEY -> {
                fields.put("name", Filter.FIELD_TYPE_TEXT);
                fields.put("generation", Filter.FIELD_TYPE_NUMBER);
                fields.put("handheld", Filter.FIELD_TYPE_BOOLEAN);
            }
            case Keychain.TOY_KEY -> {
                fields.put("name", Filter.FIELD_TYPE_TEXT);
                fields.put("set", Filter.FIELD_TYPE_TEXT);
            }
            case Keychain.VIDEO_GAME_KEY -> {
                fields.put("title", Filter.FIELD_TYPE_TEXT);
                //Right now video games cannot filter on system text because the system name is not in the database table
                //we would need to join that table in the getWithFilters query but then unique filters would need to be allowed
                //for system text...
            }
            case Keychain.VIDEO_GAME_BOX_KEY -> {
                fields.put("title", Filter.FIELD_TYPE_TEXT);
                fields.put("is_physical", Filter.FIELD_TYPE_BOOLEAN);
                fields.put("is_collection", Filter.FIELD_TYPE_BOOLEAN);
                //Video game boxes have the same issue where we can't filter on the games inside the box because they are
                //not on the table through the base query, but adjustments could be made in the future...
            }
            case Keychain.BOARD_GAME_KEY -> {
                fields.put("title", Filter.FIELD_TYPE_TEXT);
            }
            case Keychain.BOARD_GAME_BOX_KEY -> {
                fields.put("title", Filter.FIELD_TYPE_TEXT);
                fields.put("is_expansion", Filter.FIELD_TYPE_BOOLEAN);
                fields.put("is_stand_alone", Filter.FIELD_TYPE_BOOLEAN);
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
