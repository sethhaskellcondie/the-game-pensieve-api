package com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;

import java.util.List;

public record BoardGameBoxRequestDto(
        String title,
        boolean isExpansion,
        boolean isStandAlone,
        Integer baseSetId, //this is a board game box id can be null
        Integer boardGameId, //this is a board game id leave blank or at 0 to create a new board game for this box using the same name
        List<CustomFieldValue> customFieldValues
) {
}
