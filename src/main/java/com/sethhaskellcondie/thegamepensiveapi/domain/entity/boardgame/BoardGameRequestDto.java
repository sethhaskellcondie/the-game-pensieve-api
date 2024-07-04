package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;

import java.util.List;

public record BoardGameRequestDto(
        String title,
        List<Integer> boardGameBoxIds,
        List<CustomFieldValue> customFieldValues
) {
}
