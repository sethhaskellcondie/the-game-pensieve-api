package com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;

import java.util.List;

public record ToyRequestDto(
        String name,
        String set,
        List<CustomFieldValue> customFieldValues
) {
}
