package com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;

import java.util.List;

public record ToyRequestDto(
        String name,
        String set,
        List<CustomFieldValue> customFieldValues
) {
}
