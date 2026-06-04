package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import java.util.List;

public record CustomFieldRequestDto(String name, String type, String entityKey, List<String> options) {

    public static CustomFieldRequestDto withoutOptions(String name, String type, String entityKey) {
        return new CustomFieldRequestDto(name, type, entityKey, List.of());
    }
}
