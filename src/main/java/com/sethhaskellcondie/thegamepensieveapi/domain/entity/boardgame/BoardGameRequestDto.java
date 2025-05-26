package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;

import java.util.List;

public record BoardGameRequestDto(
        String title,
        List<CustomFieldValue> customFieldValues
) {
}
